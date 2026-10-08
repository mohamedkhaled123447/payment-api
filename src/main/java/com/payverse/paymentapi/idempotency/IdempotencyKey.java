package com.payverse.paymentapi.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyKey implements Persistable<String> {

    @Id
    @Column(name = "idempotency_key", length = 255)
    private String key;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IdempotencyKeyStatus status;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "payment_id")
    private UUID paymentId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // The key is assigned by the client, so Spring Data can't tell a new row from an existing one.
    // Marking it lets save() issue a plain INSERT, which the primary key rejects for a duplicate,
    // instead of a merge that would first SELECT.
    @Transient
    private boolean newRow;

    static IdempotencyKey start(String key, String requestHash) {
        IdempotencyKey record = new IdempotencyKey();
        record.key = key;
        record.requestHash = requestHash;
        record.status = IdempotencyKeyStatus.IN_PROGRESS;
        record.newRow = true;
        return record;
    }

    void complete(int responseStatus, String responseBody, UUID paymentId) {
        this.status = IdempotencyKeyStatus.COMPLETED;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.paymentId = paymentId;
    }

    @Override
    public String getId() {
        return key;
    }

    @Override
    public boolean isNew() {
        return newRow;
    }

    @PostPersist
    @PostLoad
    void markStored() {
        newRow = false;
    }
}
