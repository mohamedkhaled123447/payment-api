package com.payverse.paymentapi.threeds.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "three_ds_sessions")
@Data
public class ThreeDSSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ThreeDSProviderType provider;

    @Column(name = "provider_reference_id", nullable = false, length = 255)
    private String providerReferenceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ThreeDSSessionStatus status;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency;

    @Column(name = "authentication_transaction_id", length = 255)
    private String authenticationTransactionId;

    @Column(name = "authentication_value", length = 255)
    private String authenticationValue;

    @Column(length = 10)
    private String eci;

    @Column(name = "ecommerce_indicator", length = 50)
    private String ecommerceIndicator;

    @Column(length = 255)
    private String xid;

    @Column(name = "specification_version", length = 20)
    private String specificationVersion;

    @Column(name = "directory_server_transaction_id", length = 255)
    private String directoryServerTransactionId;

    @Column(name = "veres_enrolled", length = 10)
    private String veresEnrolled;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

}
