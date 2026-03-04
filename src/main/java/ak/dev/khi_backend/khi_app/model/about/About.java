package ak.dev.khi_backend.khi_app.model.about;


import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "about_pages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class About {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(unique = true)
    private String slug; // e.g., "stanford-about", "main-about"

    @NotBlank
    private String title;

    private String subtitle;

    @Column(name = "meta_description"  , length = 2500)
    private String metaDescription;

    private boolean active = true;

    @Column(name = "display_order")
    private Integer displayOrder = 0;

    @OneToMany(mappedBy = "about", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sequence ASC")
    @Builder.Default
    private List<AboutBlock> blocks = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Helper method to maintain bidirectional relationship
    public void addBlock(AboutBlock block) {
        blocks.add(block);
        block.setAbout(this);
    }

    public void removeBlock(AboutBlock block) {
        blocks.remove(block);
        block.setAbout(null);
    }
}