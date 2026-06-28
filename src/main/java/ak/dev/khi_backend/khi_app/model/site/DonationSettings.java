package ak.dev.khi_backend.khi_app.model.site;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "donation_settings")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DonationSettings {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "title_ckb", length = 500) private String titleCkb;
    @Column(name = "title_kmr", length = 500) private String titleKmr;
    @Column(name = "description_ckb", columnDefinition = "TEXT") private String descriptionCkb;
    @Column(name = "description_kmr", columnDefinition = "TEXT") private String descriptionKmr;
    @Column(name = "hero_image_url", columnDefinition = "TEXT") private String heroImageUrl;
    @Column(name = "bank_name", length = 300) private String bankName;
    @Column(name = "account_name", length = 300) private String accountName;
    @Column(name = "account_number", length = 120) private String accountNumber;
    @Column(length = 120) private String iban;
    @Column(name = "swift_code", length = 60) private String swiftCode;
    @Column(name = "payment_instructions_ckb", columnDefinition = "TEXT") private String paymentInstructionsCkb;
    @Column(name = "payment_instructions_kmr", columnDefinition = "TEXT") private String paymentInstructionsKmr;
    @Builder.Default @Column(name = "financial_enabled") private boolean financialDonationsEnabled = true;
    @Builder.Default @Column(name = "archive_enabled") private boolean archiveDonationsEnabled = true;
}
