package ak.dev.khi_backend.khi_app.repository.contact;

import ak.dev.khi_backend.khi_app.model.contact.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ContactRepository extends JpaRepository<Contact, Long> {

    /** Lookup by CKB slug. */
    Optional<Contact> findBySlugCkb(String slugCkb);

    /** Lookup by KMR slug. */
    Optional<Contact> findBySlugKmr(String slugKmr);

    /**
     * Lookup by either slug — used in public-facing slug routing.
     * Returns the contact page regardless of which language slug is used.
     */
    Optional<Contact> findBySlugCkbOrSlugKmr(String slugCkb, String slugKmr);

    /** All active contact pages ordered by displayOrder. */
    Page<Contact> findAllByActiveTrueOrderByDisplayOrderAsc(Pageable pageable);
}
