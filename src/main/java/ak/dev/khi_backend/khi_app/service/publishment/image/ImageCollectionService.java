package ak.dev.khi_backend.khi_app.service.publishment.image;


import ak.dev.khi_backend.khi_app.dto.publishment.image.ImageAlbumItemDTO;
import ak.dev.khi_backend.khi_app.dto.publishment.image.ImageCollectionDTO;
import ak.dev.khi_backend.khi_app.dto.publishment.image.ImageCollectionLogDTO;
import ak.dev.khi_backend.khi_app.dto.publishment.image.ImageCollectionMapper;
import ak.dev.khi_backend.khi_app.model.publishment.image.ImageAlbumItem;
import ak.dev.khi_backend.khi_app.model.publishment.image.ImageCollection;
import ak.dev.khi_backend.khi_app.model.publishment.image.ImageCollectionLog;
import ak.dev.khi_backend.khi_app.repository.publishment.image.ImageCollectionLogRepository;
import ak.dev.khi_backend.khi_app.repository.publishment.image.ImageCollectionRepository;
import ak.dev.khi_backend.khi_app.service.S3Service;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageCollectionService {

    private final ImageCollectionRepository imageCollectionRepository;
    private final ImageCollectionLogRepository imageCollectionLogRepository;
    private final S3Service s3Service;

    private static final String ACTION_CREATED = "CREATED";
    private static final String ACTION_UPDATED = "UPDATED";
    private static final String ACTION_DELETED = "DELETED";

    // ═══════════════════════════════════════════════════════════════════
    //  1. ADD
    // ═══════════════════════════════════════════════════════════════════
    @Transactional
    public ImageCollectionDTO addImageCollection(
            ImageCollectionDTO dto,
            MultipartFile coverFile,
            List<MultipartFile> albumFiles) {

        ImageCollection collection = ImageCollectionMapper.toEntity(dto);

        // Upload cover to S3
        if (coverFile != null && !coverFile.isEmpty()) {
            collection.setCoverUrl(uploadToS3(coverFile));
        }

        // Upload album images to S3 and link with descriptions
        List<ImageAlbumItemDTO> albumItemDTOs = dto.getImageAlbum();
        if (albumFiles != null && !albumFiles.isEmpty()) {
            List<ImageAlbumItem> items = new ArrayList<>();
            for (int i = 0; i < albumFiles.size(); i++) {
                MultipartFile file = albumFiles.get(i);
                if (file == null || file.isEmpty()) continue;

                String imageUrl = uploadToS3(file);

                ImageAlbumItem item = ImageAlbumItem.builder()
                        .imageUrl(imageUrl)
                        .sortOrder(i + 1)
                        .imageCollection(collection)
                        .build();

                // Attach optional descriptions from DTO if available
                if (albumItemDTOs != null && i < albumItemDTOs.size()) {
                    ImageAlbumItemDTO itemDTO = albumItemDTOs.get(i);
                    item.setDescriptionCkb(itemDTO.getDescriptionCkb());
                    item.setDescriptionKmr(itemDTO.getDescriptionKmr());
                    if (itemDTO.getSortOrder() != null) {
                        item.setSortOrder(itemDTO.getSortOrder());
                    }
                }

                items.add(item);
            }
            collection.setImageAlbum(items);
        }

        ImageCollection saved = imageCollectionRepository.save(collection);
        logAction(saved.getId(), getTitle(saved), ACTION_CREATED,
                "Image collection created with " + saved.getImageAlbum().size() + " images");

        return ImageCollectionMapper.toDTO(saved);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  2. GET ALL
    // ═══════════════════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public Page<ImageCollectionDTO> getAllImageCollections(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return imageCollectionRepository.findAll(pageable).map(ImageCollectionMapper::toDTO);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  3. SEARCH BY KEYWORDS
    // ═══════════════════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public Page<ImageCollectionDTO> searchByKeyword(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return imageCollectionRepository.searchByKeyword(keyword.trim(), pageable)
                .map(ImageCollectionMapper::toDTO);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  4. SEARCH BY TAGS
    // ═══════════════════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public Page<ImageCollectionDTO> searchByTag(String tag, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return imageCollectionRepository.searchByTag(tag.trim(), pageable)
                .map(ImageCollectionMapper::toDTO);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  5. UPDATE (re-upload files only if new ones are provided)
    // ═══════════════════════════════════════════════════════════════════
    @Transactional
    public ImageCollectionDTO updateImageCollection(
            Long id,
            ImageCollectionDTO dto,
            MultipartFile coverFile,
            List<MultipartFile> albumFiles) {

        ImageCollection collection = findOrThrow(id);
        ImageCollectionMapper.updateEntity(collection, dto);

        // Upload new cover only if provided
        if (coverFile != null && !coverFile.isEmpty()) {
            collection.setCoverUrl(uploadToS3(coverFile));
        }

        // Upload new album images only if provided (replaces existing)
        List<ImageAlbumItemDTO> albumItemDTOs = dto.getImageAlbum();
        if (albumFiles != null && !albumFiles.isEmpty()) {
            collection.getImageAlbum().clear();

            List<ImageAlbumItem> items = new ArrayList<>();
            for (int i = 0; i < albumFiles.size(); i++) {
                MultipartFile file = albumFiles.get(i);
                if (file == null || file.isEmpty()) continue;

                String imageUrl = uploadToS3(file);

                ImageAlbumItem item = ImageAlbumItem.builder()
                        .imageUrl(imageUrl)
                        .sortOrder(i + 1)
                        .imageCollection(collection)
                        .build();

                if (albumItemDTOs != null && i < albumItemDTOs.size()) {
                    ImageAlbumItemDTO itemDTO = albumItemDTOs.get(i);
                    item.setDescriptionCkb(itemDTO.getDescriptionCkb());
                    item.setDescriptionKmr(itemDTO.getDescriptionKmr());
                    if (itemDTO.getSortOrder() != null) {
                        item.setSortOrder(itemDTO.getSortOrder());
                    }
                }

                items.add(item);
            }
            collection.getImageAlbum().addAll(items);
        }

        ImageCollection updated = imageCollectionRepository.save(collection);
        logAction(updated.getId(), getTitle(updated), ACTION_UPDATED, "Image collection updated");

        return ImageCollectionMapper.toDTO(updated);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  6. DELETE
    // ═══════════════════════════════════════════════════════════════════
    @Transactional
    public void deleteImageCollection(Long id) {
        ImageCollection collection = findOrThrow(id);
        String title = getTitle(collection);
        imageCollectionRepository.delete(collection);
        logAction(id, title, ACTION_DELETED, "Image collection deleted permanently");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  LOGS
    // ═══════════════════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public Page<ImageCollectionLogDTO> getAllLogs(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return imageCollectionLogRepository.findAllByOrderByTimestampDesc(pageable)
                .map(ImageCollectionMapper::toLogDTO);
    }

    @Transactional(readOnly = true)
    public Page<ImageCollectionLogDTO> getLogsByCollectionId(Long collectionId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return imageCollectionLogRepository.findByImageCollectionIdOrderByTimestampDesc(collectionId, pageable)
                .map(ImageCollectionMapper::toLogDTO);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private ImageCollection findOrThrow(Long id) {
        return imageCollectionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Image collection not found with id: " + id));
    }

    private String uploadToS3(MultipartFile file) {
        try {
            return s3Service.upload(file.getBytes(), file.getOriginalFilename(), file.getContentType());
        } catch (IOException e) {
            log.error("Failed to upload file to S3: {}", e.getMessage());
            throw new RuntimeException("Failed to upload file: " + e.getMessage(), e);
        }
    }

    private void logAction(Long collectionId, String title, String action, String details) {
        imageCollectionLogRepository.save(ImageCollectionLog.builder()
                .imageCollectionId(collectionId)
                .collectionTitle(title)
                .action(action)
                .details(details)
                .timestamp(LocalDateTime.now())
                .build());
    }

    private String getTitle(ImageCollection collection) {
        if (collection.getCkbContent() != null && collection.getCkbContent().getTitle() != null) {
            return collection.getCkbContent().getTitle();
        }
        if (collection.getKmrContent() != null && collection.getKmrContent().getTitle() != null) {
            return collection.getKmrContent().getTitle();
        }
        return "Untitled Collection";
    }
}