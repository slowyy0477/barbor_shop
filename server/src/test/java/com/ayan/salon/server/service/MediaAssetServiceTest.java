package com.ayan.salon.server.service;

import com.ayan.salon.server.domain.MediaAsset;
import com.ayan.salon.server.domain.DomainTypes.ActorRole;
import com.ayan.salon.server.domain.repository.MediaAssetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaAssetServiceTest {
    private final UUID salon = UUID.randomUUID();
    private final ActorContext owner = new ActorContext(UUID.randomUUID(), salon, ActorRole.OWNER, Set.of());
    private final ActorContext customer = new ActorContext(UUID.randomUUID(), salon, ActorRole.CUSTOMER, Set.of());

    @Mock MediaAssetRepository assets;

    @Test
    void ownerCanUploadValidatedLogoAndReceivesOpaquePublicUri() {
        MediaAssetService service = new MediaAssetService(assets, 2 * 1024 * 1024);
        byte[] jpeg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x01};
        MockMultipartFile file = new MockMultipartFile("file", "logo.jpg", "IMAGE/JPEG", jpeg);
        when(assets.save(any(MediaAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MediaAssetService.Upload result = service.upload(owner, salon, file, " logo ");

        assertEquals("image/jpeg", result.contentType());
        assertEquals(jpeg.length, result.sizeBytes());
        assertTrue(result.uri().matches("/api/public/media/[0-9a-f-]{36}"));
        verify(assets).save(any(MediaAsset.class));
    }

    @Test
    void customerCannotUploadLogoOrHaircutMedia() {
        MediaAssetService service = new MediaAssetService(assets, 2 * 1024 * 1024);
        MockMultipartFile file = new MockMultipartFile("file", "logo.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});

        assertThrows(ActorContext.AuthorizationException.class,
                () -> service.upload(customer, salon, file, "LOGO"));
        verify(assets, never()).save(any(MediaAsset.class));
    }

    @Test
    void uploadRejectsMimeSpoofAndOversizedPayloadBeforePersisting() {
        MediaAssetService service = new MediaAssetService(assets, 32_768);
        MockMultipartFile spoofed = new MockMultipartFile("file", "logo.png", "image/png",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
        assertThrows(IllegalArgumentException.class,
                () -> service.upload(owner, salon, spoofed, "LOGO"));

        byte[] oversized = new byte[32_769];
        MockMultipartFile large = new MockMultipartFile("file", "logo.jpg", "image/jpeg", oversized);
        assertThrows(IllegalArgumentException.class,
                () -> service.upload(owner, salon, large, "LOGO"));
        verify(assets, never()).save(any(MediaAsset.class));
    }

    @Test
    void publicAssetIsSalonIndependentAndMissingIdIsReported() {
        MediaAssetService service = new MediaAssetService(assets, 2 * 1024 * 1024);
        UUID id = UUID.randomUUID();
        MediaAsset asset = new MediaAsset(salon, "LOGO", "image/png", new byte[]{
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
        when(assets.findById(id)).thenReturn(Optional.of(asset));

        assertEquals(asset, service.publicAsset(id));
        assertThrows(IllegalArgumentException.class, () -> service.publicAsset(null));
    }
}
