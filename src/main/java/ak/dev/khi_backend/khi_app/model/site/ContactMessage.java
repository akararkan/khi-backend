package ak.dev.khi_backend.khi_app.model.site;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "contact_messages", indexes = {
        @Index(name = "idx_contact_message_status_created", columnList = "status,created_at")
})
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ContactMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 200) private String name;
    @Column(nullable = false, length = 254) private String email;
    @Column(length = 60) private String phone;
    @Column(nullable = false, length = 300) private String subject;
    @Column(nullable = false, columnDefinition = "TEXT") private String message;
    @Column(length = 10) private String locale;
    @Builder.Default @Column(nullable = false, length = 30) private String status = "NEW";
    @CreationTimestamp @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
}
