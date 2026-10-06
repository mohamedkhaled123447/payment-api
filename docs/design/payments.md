# Payments design

Status: **draft for review**. Decisions D1–D7 were agreed in the design session; D8–D13 are proposals awaiting review.

## 1. Context

Today the service only does 3DS (Cybersource Payer Authentication): setup → enrollment → challenge → validation. It ends with CAVV/ECI stored on a `three_ds_sessions` row, and nothing uses them. There is no payment record: `POST /api/v1/3ds/setup` invents a `paymentId` that points at no row.

This document defines the payment domain and plans the work in vertical slices. Section 7 specifies **slice 1** in enough detail to implement.

## 2. Decision log

### Agreed

| # | Decision | Why | Rejected alternative |
|---|---|---|---|
| D1 | Support both **auth + capture** and **sale** (authorization with capture in one call). | Some merchants reserve funds and take them later (e.g. on shipping); others take them immediately. | Sale only: blocks the reserve-then-take use case. |
| D2 | **DECLINED** and **FAILED** are separate states. | A bank decline is a business answer; a technical error is not. They are reported, retried and reconciled differently. | One `FAILED` state: loses the difference support and reporting need. |
| D3 | A timeout or crash during authorization leaves the payment in **`AUTHORIZING`**, never `FAILED`. | The bank may have approved the request and only the response was lost. Marking it failed invites a retry, which places a second hold on the customer's funds. | Mark failed and let the customer retry: double holds, chargebacks. |
| D4 | **Save the payment, with our own reference, before calling the provider.** The payment id is the reference sent to Cybersource as `clientReferenceInformation.code`. | If we crash while waiting, the saved row is the only way to find and resolve the request later. | Save after the call: a crash loses the record of a possibly approved authorization. |
| D5 | A **recovery job** resolves payments stuck in `AUTHORIZING` longer than a threshold (e.g. 2 minutes): query the provider by our reference first, and reverse the authorization if the outcome is still unclear. | Neither we nor the customer may retry blindly. The threshold avoids touching requests that are still waiting for their response. | Blind retry: double charges. |
| D6 | Client retries are made safe with an **`Idempotency-Key` header**, enforced by a database **`UNIQUE` constraint**. Same key with a different body → `422`; same key while the first request is still running → `409`; same key, same body → the stored response is replayed. | A unique constraint is atomic and works across all pods; check-then-insert has a race window. | Row locks: the row doesn't exist yet. Table lock: serializes every payment. |
| D7 | **The payment is created first** and owns amount and currency. 3DS setup takes an existing `paymentId`; enrollment reads amount and currency from the payment. | No orphan 3DS sessions; the client can't authenticate a different amount from the one it will be charged; payments that skip 3DS (recurring, exemptions) fit the same model. | 3DS creates the payment id: the amount lives in the wrong place and abandoned sessions point at nothing. |

### Proposed (please review)

| # | Proposal | Why | Alternative |
|---|---|---|---|
| D8 | Amounts are **decimal strings** in the API (`"49.90"`) and `NUMERIC(19,4)` in the DB, validated against the currency's minor-unit digits (`java.util.Currency.getDefaultFractionDigits`, so `"49.905"` USD and `"10.5"` JPY are rejected). | Matches the existing 3DS columns and Cybersource's decimal `totalAmount`; strings avoid JSON float rounding. | Integer minor units (`4990`), as Stripe does: unambiguous, but conversion is needed for Cybersource and existing columns. |
| D9 | A separate **`idempotency_keys` table** rather than a column on `payments`. | Capture, void and refund will need idempotency too; one table and one mechanism serve every write endpoint. | Unique column on `payments`: simpler now, duplicated for each later operation. |
| D10 | The request hash is **SHA-256 of the validated request object**, not of the raw bytes. | Whitespace or field-order differences in a client retry must not trigger a false `422`. | Hash of raw body: brittle. |
| D11 | Keys are **unique globally** until clients are authenticated; then the constraint becomes `UNIQUE (client_id, key)`. | There is no client identity yet (no API authentication). | — |
| D12 | The new code goes in a **`payment` module with a behavioural domain entity**: `Payment` enforces its own transitions, and illegal ones throw. The `threeds` module stays as it is for now. | This was the main finding of the architecture review; starting the new module this way avoids refactoring it later. | Anemic entity with setters, like `ThreeDSSession`: rules end up scattered across services. |
| D13 | The existing `three_ds_sessions.payment_id` gets a foreign key to `payments(id)` added as **`NOT VALID`**. | Existing rows reference ids that have no payment; `NOT VALID` enforces the key for new rows without failing the migration on old ones. | Delete old sessions first: destroys test data in shared environments. |

## 3. State machine

Target model. Java enum names can't start with a digit, so the 3DS state is `THREE_DS_PENDING`.

```text
                                         ┌─► AUTHORIZED ─┬─capture─► CAPTURED   (final*)
                                         │               └─void────► VOIDED     (final)
CREATED ─► THREE_DS_PENDING ─► AUTHORIZING ─┼─► PENDING_REVIEW ─► AUTHORIZED | DECLINED
   │                               ▲     ├─► DECLINED                          (final)
   └───────── (no 3DS) ────────────┘     └─► FAILED                            (final)

sale: AUTHORIZING ─► CAPTURED
* final until refunds are added
```

- `AUTHORIZING` is saved **before** the provider call (D4). A crash and a timeout both leave the payment there, and the recovery job (D5) resolves it.
- Later additions: `REFUNDED` / `PARTIALLY_REFUNDED`, `EXPIRED` (abandoned `CREATED` payments, lapsed authorizations).

### Transitions per slice

| From | To | Trigger | Slice |
|---|---|---|---|
| — | `CREATED` | `POST /payments` | 1 |
| `CREATED` | `THREE_DS_PENDING` | `POST /3ds/setup` | 1 |
| `THREE_DS_PENDING`, `CREATED` | `AUTHORIZING` | authorize request (saved before the call) | 2 |
| `AUTHORIZING` | `AUTHORIZED` / `PENDING_REVIEW` / `DECLINED` / `FAILED` / `CAPTURED` (sale) | provider response | 2 |
| `AUTHORIZING` | any of the above | recovery job | 3 |
| `AUTHORIZED` | `CAPTURED` / `VOIDED` | capture / void | 4 |

## 4. Vertical slices

1. **Payment aggregate.** `payments` and `idempotency_keys` tables, `POST /payments`, `GET /payments/{id}`, the state machine, and 3DS linked to an existing payment. No gateway call.
2. **Authorization.** Uses the CAVV/ECI from the 3DS session; saves `AUTHORIZING` before the call; maps Cybersource results; supports sale.
3. **Recovery job.** Resolves stuck `AUTHORIZING` payments by querying by reference, then reversing.
4. **Capture and void.**
5. **Later.** Refunds, authorization expiry, webhooks, reconciliation, audit events.

## 5. Package layout (slice 1)

```text
com.payverse.paymentapi
├── payment
│   ├── api            PaymentController, CreatePaymentRequest, PaymentResponse, PaymentExceptionHandler
│   ├── application    PaymentService, PaymentNotFoundException, PaymentInvalidStateException
│   ├── domain         Payment (entity with transition methods), PaymentStatus, CaptureMethod
│   └── persistence    PaymentRepository
├── idempotency        IdempotencyService, IdempotencyKey (entity), repository, exceptions
└── threeds            unchanged layout; setup and enrollment change (section 7.4)
```

## 6. Open questions

| # | Question | Needed by |
|---|---|---|
| O1 | When 3DS fails (`AUTHENTICATION_FAILED`) or is unavailable, does the payment become `FAILED`, or may it still be authorized without 3DS (liability stays with the merchant)? | Slice 2 |
| O2 | How long are idempotency keys kept? Stripe keeps them 24 hours. A cleanup job comes later. | Slice 1 (retention value only) |
| O3 | Do we add Testcontainers for PostgreSQL integration tests? The race test in 7.6 needs a real database. Does GitLab CI have Docker available? | Slice 1 |
| O4 | API authentication and client identity (D11). | Before production |
| O5 | Can a payment run 3DS more than once (e.g. after an abandoned challenge)? Today `payment_id` is unique on `three_ds_sessions`, so no. | Slice 2 |

## 7. Slice 1 specification

### 7.1 `POST /api/v1/payments`

Request headers:

| Header | Rule |
|---|---|
| `Idempotency-Key` | Required. 1–255 characters. The client generates one per payment attempt (a UUID is recommended) and reuses it on every retry of that attempt. |
| `Content-Type` | `application/json` |

Request body:

```json
{
  "amount": "49.90",
  "currency": "USD",
  "captureMethod": "MANUAL",
  "merchantReference": "order-1042"
}
```

| Field | Rule |
|---|---|
| `amount` | Required. Decimal string, > 0, scale ≤ the currency's minor-unit digits (D8). |
| `currency` | Required. ISO 4217 code known to `java.util.Currency`; stored upper-case. |
| `captureMethod` | Required. `MANUAL` (auth, capture later) or `AUTOMATIC` (sale) (D1). |
| `merchantReference` | Optional. ≤ 100 characters. The merchant's own order id; not unique. |

Response `201 Created`, with a `Location: /api/v1/payments/{id}` header:

```json
{
  "id": "6f1c…",
  "status": "CREATED",
  "amount": "49.90",
  "currency": "USD",
  "captureMethod": "MANUAL",
  "merchantReference": "order-1042",
  "createdAt": "2026-10-06T19:00:00Z"
}
```

| Case | Status | Body |
|---|---|---|
| New key | `201` | the payment |
| Same key, same body | the stored status (`201`) | the stored body, plus header `Idempotent-Replayed: true` |
| Same key, different body | `422` | `ProblemDetail`: key reused with a different request |
| Same key, first request still in progress | `409` | `ProblemDetail`: retry later |
| Missing or invalid header or body | `400` | `ProblemDetail` with field errors |

In slice 1, creating a payment is a single database transaction with no external call. A concurrent duplicate's insert therefore blocks on the unique index until the first commits, then sees `COMPLETED` and replays, so `409` is practically unreachable. It becomes reachable in slice 2, where the provider call runs outside the transaction.

### 7.2 `GET /api/v1/payments/{id}`

`200` with the same body as above, or `404` `ProblemDetail`. No idempotency key is needed for reads.

### 7.3 Migration `004-create-payments-and-idempotency-keys.sql`

```sql
CREATE TABLE payments (
    id                 UUID           NOT NULL,
    status             VARCHAR(30)    NOT NULL,
    amount             NUMERIC(19, 4) NOT NULL,
    currency           VARCHAR(3)     NOT NULL,
    capture_method     VARCHAR(20)    NOT NULL,
    merchant_reference VARCHAR(100),
    version            BIGINT         NOT NULL DEFAULT 0,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT ck_payments_amount_positive CHECK (amount > 0)
);

CREATE TABLE idempotency_keys (
    idempotency_key  VARCHAR(255) NOT NULL,
    request_hash     VARCHAR(64)  NOT NULL,
    status           VARCHAR(20)  NOT NULL,   -- IN_PROGRESS | COMPLETED
    response_status  INTEGER,
    response_body    TEXT,
    payment_id       UUID,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_idempotency_keys PRIMARY KEY (idempotency_key),   -- the UNIQUE guarantee of D6
    CONSTRAINT fk_idempotency_keys_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
);

ALTER TABLE three_ds_sessions
    ADD CONSTRAINT fk_three_ds_sessions_payment
    FOREIGN KEY (payment_id) REFERENCES payments (id) NOT VALID;   -- D13
```

Why each choice:
- `version` gives `@Version` optimistic locking, as on `three_ds_sessions`, so two concurrent transitions can't both win.
- The `CHECK` repeats the API validation at the database level, so a bug in any code path can't store a zero or negative amount.
- The primary key on `idempotency_key` is the unique constraint that settles the race (D6).
- There is no index on `payments.status` yet. Slice 3's recovery job adds `(status, updated_at)` when it needs it.

### 7.4 Changes to existing 3DS code (breaking)

| Endpoint | Change |
|---|---|
| `POST /3ds/setup` | Request gains a required `paymentId`. The service loads the payment (`404` if missing), requires `CREATED` (`409` otherwise), moves it to `THREE_DS_PENDING`, and creates the session with that id. The response still echoes `paymentId`. |
| `POST /3ds/enrollment` | `amount` and `currency` are **removed** from the request. The service reads them from the payment and still copies them onto the session as a record of what was authenticated. |
| `POST /3ds/validation`, challenge callback | Unchanged. |

Also update: `dev-tools/3ds-test.html` (add a "create payment" step before setup) and the README.

### 7.5 Implementation order

Each step builds and passes the tests on its own.

1. Migration `004`.
2. Domain: `PaymentStatus` with its allowed transitions, `Payment` with `create(...)` and `markThreeDSPending()`, and unit tests covering every legal and illegal transition.
3. Idempotency: `IdempotencyService` (insert `IN_PROGRESS` → run the action → store the response as `COMPLETED`; on unique violation, compare hashes and replay, `422` or `409`).
4. `PaymentService.create` / `get`, `PaymentController`, exception handler.
5. 3DS changes from 7.4, with updated tests.
6. `dev-tools/3ds-test.html` and README.

### 7.6 Verification

- Unit: the state-machine transition table; amount and currency validation (D8); request hashing (D10).
- `@WebMvcTest`: `201`, replay with `Idempotent-Replayed`, `422`, `400` for a missing header or invalid body, `404` on GET.
- Against PostgreSQL (Testcontainers if O3 is agreed, otherwise a local database): migration `004` applies on a database already at `003`; two concurrent `POST /payments` with the same key create exactly one payment; the `CHECK` rejects a zero amount.
- Manual: run the app, create a payment, then run setup → enrollment through `dev-tools/3ds-test.html`.
