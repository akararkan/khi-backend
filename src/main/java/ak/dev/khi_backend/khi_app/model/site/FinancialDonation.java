package ak.dev.khi_backend.khi_app.model.site;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "financial_donations", indexes = {
        @Index(name = "idx_financial_donation_status_created", columnList = "status,created_at")
})
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class FinancialDonation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "donor_name", nullable = false, length = 200) private String donorName;
    @Column(nullable = false, length = 254) private String email;
    @Column(length = 60) private String phone;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 10) private String currency;
    @Column(name = "payment_method", nullable = false, length = 80) private String paymentMethod;
    @Column(name = "transaction_reference", length = 200) private String transactionReference;
    @Column(columnDefinition = "TEXT") private String message;
    @Builder.Default @Column(nullable = false, length = 30) private String status = "PENDING";
    @CreationTimestamp @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
}
