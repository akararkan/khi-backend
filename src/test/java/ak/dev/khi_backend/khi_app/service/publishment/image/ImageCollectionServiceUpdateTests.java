package ak.dev.khi_backend.khi_app.service.publishment.image;

import ak.dev.khi_backend.khi_app.dto.publishment.image.ImageCollectionDTO.ImageItemDto;
import ak.dev.khi_backend.khi_app.dto.publishment.image.ImageCollectionDTO.Response;
import ak.dev.khi_backend.khi_app.dto.publishment.image.ImageCollectionDTO.UpdateRequest;
import ak.dev.khi_backend.khi_app.enums.publishment.ImageCollectionType;
import ak.dev.khi_backend.khi_app.enums.Language;
import ak.dev.khi_backend.khi_app.exceptions.publishment.image.ImageCollectionValidationException;
import ak.dev.khi_backend.khi_app.model.publishment.image.ImageAlbumItem;
import ak.dev.khi_backend.khi_app.model.publishment.image.ImageCollection;
import ak.dev.khi_backend.khi_app.model.publishment.image.ImageContent;
import ak.dev.khi_backend.khi_app.repository.publishment.image.ImageCollectionLogRepository;
import ak.dev.khi_backend.khi_app.repository.publishment.image.ImageCollectionRepository;
import ak.dev.khi_backend.khi_app.repository.publishment.topic.PublishmentTopicRepository;
import ak.dev.khi_backend.khi_app.service.S3Service;
import ak.dev.khi_backend.khi_app.service.media.TiptapHtmlProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageCollectionServiceUpdateTests {

    @Mock private ImageCollectionRepository imageCollectionRepository;
    @Mock private ImageCollectionLogRepository imageCollectionLogRepository;
    @Mock private PublishmentTopicRepository topicRepository;
    @Mock private S3Service s3Service;
    @Mock private TiptapHtmlProcessor tiptapHtmlProcessor;

    @InjectMocks
    private ImageCollectionService imageCollectionService;

    @Test
    void existingAlbumItemKeepsSourceAndMetadataWhenUpdateOmitsSource() {
        ImageCollection collection = collectionWithExistingItem();
        when(imageCollectionRepository.findByIdWithGraph(8L))
                .thenReturn(Optional.of(collection));
        when(imageCollectionRepository.save(collection)).thenReturn(collection);

        UpdateRequest request = UpdateRequest.builder()
                .imageAlbum(List.of(ImageItemDto.builder()
                        .id(3L)
                        .captionCkb("updated caption")
                        .sortOrder(0)
                        .build()))
                .build();

        Response response = imageCollectionService.update(
                8L, request, null, null, null, null);

        assertThat(response.getImageAlbum()).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo(3L);
            assertThat(item.getImageUrl()).isEqualTo("https://cdn.example.com/original.jpg");
            assertThat(item.getFileSizeBytes()).isEqualTo(2048L);
            assertThat(item.getWidthPx()).isEqualTo(1200);
            assertThat(item.getHeightPx()).isEqualTo(800);
            assertThat(item.getCaptionCkb()).isEqualTo("updated caption");
        });
        assertThat(response.getCkbContent().getTitle()).isEqualTo("existing title");
        verify(s3Service, never()).upload(any(byte[].class), any(), any());
    }

    @Test
    void validatesWholeAlbumBeforeUploadingAnyFile() {
        ImageCollection collection = collectionWithExistingItem();
        when(imageCollectionRepository.findByIdWithGraph(8L))
                .thenReturn(Optional.of(collection));

        UpdateRequest request = UpdateRequest.builder()
                .collectionType(ImageCollectionType.GALLERY)
                .imageAlbum(List.of(
                        ImageItemDto.builder().captionCkb("new upload").build(),
                        ImageItemDto.builder().captionCkb("missing source").build()
                ))
                .build();
        MockMultipartFile upload = new MockMultipartFile(
                "images", "new.jpg", "image/jpeg", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> imageCollectionService.update(
                8L, request, null, null, null, List.of(upload)))
                .isInstanceOf(ImageCollectionValidationException.class)
                .hasMessageContaining("image.source.required");

        verify(s3Service, never()).upload(any(byte[].class), any(), any());
        verify(imageCollectionRepository, never()).save(any());
    }

    @Test
    void rejectsAlbumItemIdThatDoesNotBelongToCollection() {
        ImageCollection collection = collectionWithExistingItem();
        when(imageCollectionRepository.findByIdWithGraph(8L))
                .thenReturn(Optional.of(collection));

        UpdateRequest request = UpdateRequest.builder()
                .imageAlbum(List.of(ImageItemDto.builder().id(999L).build()))
                .build();

        assertThatThrownBy(() -> imageCollectionService.update(
                8L, request, null, null, null, null))
                .isInstanceOf(ImageCollectionValidationException.class);

        verify(s3Service, never()).upload(any(byte[].class), any(), any());
        verify(imageCollectionRepository, never()).save(any());
    }

    private ImageCollection collectionWithExistingItem() {
        ImageCollection collection = ImageCollection.builder()
                .id(8L)
                .collectionType(ImageCollectionType.SINGLE)
                .contentLanguages(new java.util.LinkedHashSet<>(List.of(Language.CKB)))
                .ckbContent(ImageContent.builder()
                        .title("existing title")
                        .description("existing description")
                        .build())
                .build();
        ImageAlbumItem item = ImageAlbumItem.builder()
                .id(3L)
                .imageCollection(collection)
                .imageUrl("https://cdn.example.com/original.jpg")
                .fileSizeBytes(2048L)
                .widthPx(1200)
                .heightPx(800)
                .mimeType("image/jpeg")
                .sortOrder(0)
                .build();
        collection.getImageAlbum().add(item);
        return collection;
    }
}
