package ak.dev.khi_backend.khi_app.service.site;

import ak.dev.khi_backend.khi_app.dto.site.SiteContentDtos.*;
import ak.dev.khi_backend.khi_app.model.site.*;
import ak.dev.khi_backend.khi_app.repository.site.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SiteContentService {

    private static final Set<String> FEATURED_TYPES =
            Set.of("book", "audio", "video", "article", "gallery", "archive");
    private static final Set<String> SUBMISSION_STATUSES =
            Set.of("NEW", "PENDING", "IN_REVIEW", "APPROVED", "COMPLETED", "REJECTED", "CLOSED");

    private final FeaturedItemRepository featuredRepository;
    private final TeamMemberRepository teamRepository;
    private final PartnerRepository partnerRepository;
    private final ContactMessageRepository contactMessageRepository;
    private final SocialLinkRepository socialLinkRepository;
    private final DonationSettingsRepository donationSettingsRepository;
    private final FinancialDonationRepository financialDonationRepository;
    private final ArchiveDonationRepository archiveDonationRepository;

    // Featured

    @Transactional(readOnly = true)
    public List<FeaturedResponse> getFeatured(String locale) {
        List<FeaturedItem> items;
        if (locale == null || locale.isBlank()) {
            items = featuredRepository.findAllByActiveTrueOrderByDisplayOrderAsc();
        } else {
            items = new ArrayList<>(featuredRepository
                    .findAllByActiveTrueAndLocaleIgnoreCaseOrderByDisplayOrderAsc(locale.trim()));
            items.addAll(featuredRepository.findAllByActiveTrueAndLocaleIsNullOrderByDisplayOrderAsc());
            items.sort(Comparator.comparing(FeaturedItem::getDisplayOrder));
        }
        return items.stream().map(this::featuredResponse).toList();
    }

    @Transactional
    public FeaturedResponse createFeatured(FeaturedRequest request) {
        FeaturedItem item = new FeaturedItem();
        applyFeatured(item, request);
        return featuredResponse(featuredRepository.save(item));
    }

    @Transactional
    public FeaturedResponse updateFeatured(Long id, FeaturedRequest request) {
        FeaturedItem item = featuredRepository.findById(id)
                .orElseThrow(() -> notFound("Featured item", id));
        applyFeatured(item, request);
        return featuredResponse(featuredRepository.save(item));
    }

    @Transactional
    public void deleteFeatured(Long id) {
        if (!featuredRepository.existsById(id)) throw notFound("Featured item", id);
        featuredRepository.deleteById(id);
    }

    private void applyFeatured(FeaturedItem item, FeaturedRequest request) {
        String type = request.getType().trim().toLowerCase(Locale.ROOT);
        if (!FEATURED_TYPES.contains(type)) {
            throw new IllegalArgumentException("Unsupported featured type: " + request.getType());
        }
        item.setType(type);
        item.setSlug(request.getSlug().trim());
        item.setTitle(request.getTitle().trim());
        item.setDescription(request.getDescription());
        item.setImageUrl(request.getImageUrl().trim());
        item.setImageAlt(trimToNull(request.getImageAlt()));
        item.setLocale(trimToNull(request.getLocale()));
        item.setDisplayOrder(defaultOrder(request.getDisplayOrder()));
        item.setActive(request.getActive() == null || request.getActive());
    }

    // Team and partners

    @Transactional(readOnly = true)
    public List<TeamMemberResponse> getTeam() {
        return teamRepository.findAllByActiveTrueOrderByDisplayOrderAsc().stream()
                .map(this::teamResponse).toList();
    }

    @Transactional
    public TeamMemberResponse createTeamMember(TeamMemberRequest request) {
        TeamMember member = new TeamMember();
        applyTeam(member, request);
        return teamResponse(teamRepository.save(member));
    }

    @Transactional
    public TeamMemberResponse updateTeamMember(Long id, TeamMemberRequest request) {
        TeamMember member = teamRepository.findById(id)
                .orElseThrow(() -> notFound("Team member", id));
        applyTeam(member, request);
        return teamResponse(teamRepository.save(member));
    }

    @Transactional
    public void deleteTeamMember(Long id) {
        if (!teamRepository.existsById(id)) throw notFound("Team member", id);
        teamRepository.deleteById(id);
    }

    private void applyTeam(TeamMember member, TeamMemberRequest request) {
        member.setNameCkb(request.getNameCkb().trim());
        member.setNameKmr(trimToNull(request.getNameKmr()));
        member.setRoleCkb(request.getRoleCkb().trim());
        member.setRoleKmr(trimToNull(request.getRoleKmr()));
        member.setBioCkb(trimToNull(request.getBioCkb()));
        member.setBioKmr(trimToNull(request.getBioKmr()));
        member.setOffice(trimToNull(request.getOffice()));
        member.setImageUrl(trimToNull(request.getImageUrl()));
        member.setDisplayOrder(defaultOrder(request.getDisplayOrder()));
        member.setActive(request.getActive() == null || request.getActive());
    }

    @Transactional(readOnly = true)
    public List<PartnerResponse> getPartners() {
        return partnerRepository.findAllByActiveTrueOrderByDisplayOrderAsc().stream()
                .map(this::partnerResponse).toList();
    }

    @Transactional
    public PartnerResponse createPartner(PartnerRequest request) {
        Partner partner = new Partner();
        applyPartner(partner, request);
        return partnerResponse(partnerRepository.save(partner));
    }

    @Transactional
    public PartnerResponse updatePartner(Long id, PartnerRequest request) {
        Partner partner = partnerRepository.findById(id)
                .orElseThrow(() -> notFound("Partner", id));
        applyPartner(partner, request);
        return partnerResponse(partnerRepository.save(partner));
    }

    @Transactional
    public void deletePartner(Long id) {
        if (!partnerRepository.existsById(id)) throw notFound("Partner", id);
        partnerRepository.deleteById(id);
    }

    private void applyPartner(Partner partner, PartnerRequest request) {
        partner.setNameCkb(request.getNameCkb().trim());
        partner.setNameKmr(trimToNull(request.getNameKmr()));
        partner.setDescriptionCkb(trimToNull(request.getDescriptionCkb()));
        partner.setDescriptionKmr(trimToNull(request.getDescriptionKmr()));
        partner.setLogoUrl(trimToNull(request.getLogoUrl()));
        partner.setWebsiteUrl(trimToNull(request.getWebsiteUrl()));
        partner.setDisplayOrder(defaultOrder(request.getDisplayOrder()));
        partner.setActive(request.getActive() == null || request.getActive());
    }

    // Contact messages

    @Transactional
    public ContactMessageResponse submitContactMessage(ContactMessageRequest request) {
        ContactMessage message = ContactMessage.builder()
                .name(request.getName().trim())
                .email(request.getEmail().trim())
                .phone(trimToNull(request.getPhone()))
                .subject(request.getSubject().trim())
                .message(request.getMessage())
                .locale(trimToNull(request.getLocale()))
                .status("NEW")
                .build();
        return contactMessageResponse(contactMessageRepository.save(message));
    }

    @Transactional(readOnly = true)
    public Page<ContactMessageResponse> getContactMessages(int page, int size) {
        return contactMessageRepository.findAll(newest(page, size)).map(this::contactMessageResponse);
    }

    @Transactional
    public ContactMessageResponse updateContactMessageStatus(Long id, StatusRequest request) {
        ContactMessage message = contactMessageRepository.findById(id)
                .orElseThrow(() -> notFound("Contact message", id));
        message.setStatus(validateStatus(request.getStatus()));
        return contactMessageResponse(contactMessageRepository.save(message));
    }

    // Social links

    @Transactional(readOnly = true)
    public List<SocialLinkResponse> getSocialLinks() {
        return socialLinkRepository.findAllByActiveTrueOrderByDisplayOrderAsc().stream()
                .map(this::socialResponse).toList();
    }

    @Transactional
    public SocialLinkResponse createSocialLink(SocialLinkRequest request) {
        SocialLink link = new SocialLink();
        applySocial(link, request);
        return socialResponse(socialLinkRepository.save(link));
    }

    @Transactional
    public SocialLinkResponse updateSocialLink(Long id, SocialLinkRequest request) {
        SocialLink link = socialLinkRepository.findById(id)
                .orElseThrow(() -> notFound("Social link", id));
        applySocial(link, request);
        return socialResponse(socialLinkRepository.save(link));
    }

    @Transactional
    public void deleteSocialLink(Long id) {
        if (!socialLinkRepository.existsById(id)) throw notFound("Social link", id);
        socialLinkRepository.deleteById(id);
    }

    private void applySocial(SocialLink link, SocialLinkRequest request) {
        link.setPlatform(request.getPlatform().trim().toUpperCase(Locale.ROOT));
        link.setUrl(request.getUrl().trim());
        link.setLabelCkb(trimToNull(request.getLabelCkb()));
        link.setLabelKmr(trimToNull(request.getLabelKmr()));
        link.setDisplayOrder(defaultOrder(request.getDisplayOrder()));
        link.setActive(request.getActive() == null || request.getActive());
    }

    // Donations

    @Transactional(readOnly = true)
    public DonationSettingsResponse getDonationSettings() {
        return donationSettingsRepository.findAll().stream().findFirst()
                .map(this::donationSettingsResponse)
                .orElseGet(() -> DonationSettingsResponse.builder()
                        .financialDonationsEnabled(true)
                        .archiveDonationsEnabled(true)
                        .build());
    }

    @Transactional(readOnly = true)
    public List<DonationTypeResponse> getDonationTypes() {
        DonationSettingsResponse settings = getDonationSettings();
        return List.of(
                DonationTypeResponse.builder()
                        .code("FINANCIAL")
                        .titleCkb("بەخشینی دارایی")
                        .titleKmr("Bexşîna aborî")
                        .enabled(settings.getFinancialDonationsEnabled())
                        .build(),
                DonationTypeResponse.builder()
                        .code("ARCHIVE")
                        .titleCkb("بەخشینی ئەرشیفی")
                        .titleKmr("Bexşîna arşîvê")
                        .enabled(settings.getArchiveDonationsEnabled())
                        .build()
        );
    }

    @Transactional
    public DonationSettingsResponse saveDonationSettings(DonationSettingsRequest request) {
        DonationSettings settings = donationSettingsRepository.findAll().stream().findFirst()
                .orElseGet(DonationSettings::new);
        settings.setTitleCkb(trimToNull(request.getTitleCkb()));
        settings.setTitleKmr(trimToNull(request.getTitleKmr()));
        settings.setDescriptionCkb(trimToNull(request.getDescriptionCkb()));
        settings.setDescriptionKmr(trimToNull(request.getDescriptionKmr()));
        settings.setHeroImageUrl(trimToNull(request.getHeroImageUrl()));
        settings.setBankName(trimToNull(request.getBankName()));
        settings.setAccountName(trimToNull(request.getAccountName()));
        settings.setAccountNumber(trimToNull(request.getAccountNumber()));
        settings.setIban(trimToNull(request.getIban()));
        settings.setSwiftCode(trimToNull(request.getSwiftCode()));
        settings.setPaymentInstructionsCkb(trimToNull(request.getPaymentInstructionsCkb()));
        settings.setPaymentInstructionsKmr(trimToNull(request.getPaymentInstructionsKmr()));
        settings.setFinancialDonationsEnabled(
                request.getFinancialDonationsEnabled() == null || request.getFinancialDonationsEnabled());
        settings.setArchiveDonationsEnabled(
                request.getArchiveDonationsEnabled() == null || request.getArchiveDonationsEnabled());
        return donationSettingsResponse(donationSettingsRepository.save(settings));
    }

    @Transactional
    public FinancialDonationResponse submitFinancialDonation(FinancialDonationRequest request) {
        ensureFinancialDonationsEnabled();
        FinancialDonation donation = FinancialDonation.builder()
                .donorName(request.getDonorName().trim())
                .email(request.getEmail().trim())
                .phone(trimToNull(request.getPhone()))
                .amount(request.getAmount())
                .currency(request.getCurrency().trim().toUpperCase(Locale.ROOT))
                .paymentMethod(request.getPaymentMethod().trim())
                .transactionReference(trimToNull(request.getTransactionReference()))
                .message(trimToNull(request.getMessage()))
                .status("PENDING")
                .build();
        return financialResponse(financialDonationRepository.save(donation));
    }

    @Transactional
    public ArchiveDonationResponse submitArchiveDonation(ArchiveDonationRequest request) {
        ensureArchiveDonationsEnabled();
        ArchiveDonation donation = ArchiveDonation.builder()
                .donorName(request.getDonorName().trim())
                .email(request.getEmail().trim())
                .phone(trimToNull(request.getPhone()))
                .materialType(request.getMaterialType().trim())
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .estimatedDate(trimToNull(request.getEstimatedDate()))
                .attachmentUrl(trimToNull(request.getAttachmentUrl()))
                .status("PENDING")
                .build();
        return archiveResponse(archiveDonationRepository.save(donation));
    }

    @Transactional(readOnly = true)
    public Page<FinancialDonationResponse> getFinancialDonations(int page, int size) {
        return financialDonationRepository.findAll(newest(page, size)).map(this::financialResponse);
    }

    @Transactional(readOnly = true)
    public Page<ArchiveDonationResponse> getArchiveDonations(int page, int size) {
        return archiveDonationRepository.findAll(newest(page, size)).map(this::archiveResponse);
    }

    @Transactional
    public FinancialDonationResponse updateFinancialStatus(Long id, StatusRequest request) {
        FinancialDonation donation = financialDonationRepository.findById(id)
                .orElseThrow(() -> notFound("Financial donation", id));
        donation.setStatus(validateStatus(request.getStatus()));
        return financialResponse(financialDonationRepository.save(donation));
    }

    @Transactional
    public ArchiveDonationResponse updateArchiveStatus(Long id, StatusRequest request) {
        ArchiveDonation donation = archiveDonationRepository.findById(id)
                .orElseThrow(() -> notFound("Archive donation", id));
        donation.setStatus(validateStatus(request.getStatus()));
        return archiveResponse(archiveDonationRepository.save(donation));
    }

    private void ensureFinancialDonationsEnabled() {
        donationSettingsRepository.findAll().stream().findFirst()
                .filter(settings -> !settings.isFinancialDonationsEnabled())
                .ifPresent(settings -> { throw new IllegalStateException("Financial donations are disabled"); });
    }

    private void ensureArchiveDonationsEnabled() {
        donationSettingsRepository.findAll().stream().findFirst()
                .filter(settings -> !settings.isArchiveDonationsEnabled())
                .ifPresent(settings -> { throw new IllegalStateException("Archive donations are disabled"); });
    }

    // Mappers

    private FeaturedResponse featuredResponse(FeaturedItem item) {
        return FeaturedResponse.builder()
                .id(String.valueOf(item.getId())).type(item.getType()).slug(item.getSlug())
                .title(item.getTitle()).description(item.getDescription())
                .image(ImageDto.builder().url(item.getImageUrl()).alt(item.getImageAlt()).build())
                .locale(item.getLocale()).displayOrder(item.getDisplayOrder()).active(item.isActive()).build();
    }

    private TeamMemberResponse teamResponse(TeamMember member) {
        return TeamMemberResponse.builder()
                .id(member.getId()).nameCkb(member.getNameCkb()).nameKmr(member.getNameKmr())
                .roleCkb(member.getRoleCkb()).roleKmr(member.getRoleKmr())
                .bioCkb(member.getBioCkb()).bioKmr(member.getBioKmr())
                .office(member.getOffice()).imageUrl(member.getImageUrl())
                .displayOrder(member.getDisplayOrder()).active(member.isActive()).build();
    }

    private PartnerResponse partnerResponse(Partner partner) {
        return PartnerResponse.builder()
                .id(partner.getId()).nameCkb(partner.getNameCkb()).nameKmr(partner.getNameKmr())
                .descriptionCkb(partner.getDescriptionCkb()).descriptionKmr(partner.getDescriptionKmr())
                .logoUrl(partner.getLogoUrl()).websiteUrl(partner.getWebsiteUrl())
                .displayOrder(partner.getDisplayOrder()).active(partner.isActive()).build();
    }

    private ContactMessageResponse contactMessageResponse(ContactMessage message) {
        return ContactMessageResponse.builder()
                .id(message.getId()).name(message.getName()).email(message.getEmail())
                .phone(message.getPhone()).subject(message.getSubject()).message(message.getMessage())
                .locale(message.getLocale()).status(message.getStatus()).createdAt(message.getCreatedAt()).build();
    }

    private SocialLinkResponse socialResponse(SocialLink link) {
        return SocialLinkResponse.builder()
                .id(link.getId()).platform(link.getPlatform()).url(link.getUrl())
                .labelCkb(link.getLabelCkb()).labelKmr(link.getLabelKmr())
                .displayOrder(link.getDisplayOrder()).active(link.isActive()).build();
    }

    private DonationSettingsResponse donationSettingsResponse(DonationSettings settings) {
        return DonationSettingsResponse.builder()
                .id(settings.getId()).titleCkb(settings.getTitleCkb()).titleKmr(settings.getTitleKmr())
                .descriptionCkb(settings.getDescriptionCkb()).descriptionKmr(settings.getDescriptionKmr())
                .heroImageUrl(settings.getHeroImageUrl()).bankName(settings.getBankName())
                .accountName(settings.getAccountName()).accountNumber(settings.getAccountNumber())
                .iban(settings.getIban()).swiftCode(settings.getSwiftCode())
                .paymentInstructionsCkb(settings.getPaymentInstructionsCkb())
                .paymentInstructionsKmr(settings.getPaymentInstructionsKmr())
                .financialDonationsEnabled(settings.isFinancialDonationsEnabled())
                .archiveDonationsEnabled(settings.isArchiveDonationsEnabled()).build();
    }

    private FinancialDonationResponse financialResponse(FinancialDonation donation) {
        return FinancialDonationResponse.builder()
                .id(donation.getId()).donorName(donation.getDonorName()).email(donation.getEmail())
                .phone(donation.getPhone()).amount(donation.getAmount()).currency(donation.getCurrency())
                .paymentMethod(donation.getPaymentMethod())
                .transactionReference(donation.getTransactionReference()).message(donation.getMessage())
                .status(donation.getStatus()).createdAt(donation.getCreatedAt()).build();
    }

    private ArchiveDonationResponse archiveResponse(ArchiveDonation donation) {
        return ArchiveDonationResponse.builder()
                .id(donation.getId()).donorName(donation.getDonorName()).email(donation.getEmail())
                .phone(donation.getPhone()).materialType(donation.getMaterialType())
                .title(donation.getTitle()).description(donation.getDescription())
                .estimatedDate(donation.getEstimatedDate()).attachmentUrl(donation.getAttachmentUrl())
                .status(donation.getStatus()).createdAt(donation.getCreatedAt()).build();
    }

    private PageRequest newest(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private int defaultOrder(Integer order) {
        return order == null ? 0 : order;
    }

    private String validateStatus(String status) {
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!SUBMISSION_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported status: " + status);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private EntityNotFoundException notFound(String resource, Long id) {
        return new EntityNotFoundException(resource + " not found: " + id);
    }
}
