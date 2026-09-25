package com.ayan.salon.server.service;

import com.ayan.salon.server.domain.MediaAsset;
import com.ayan.salon.server.domain.repository.MediaAssetRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Validates and persists the small public images used by the salon catalogue. */
@Service
public class MediaAssetService {
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private final MediaAssetRepository assets;
    private final long maxBytes;

    public MediaAssetService(MediaAssetRepository assets,
                             @Value("${ayan.media.max-bytes:2097152}") long maxBytes) {
        if (maxBytes < 32_768 || maxBytes > 10L * 1024 * 1024) {
            throw new IllegalArgumentException("Media max size must be between 32 KB and 10 MB");
        }
        this.assets = assets;
        this.maxBytes = maxBytes;
    }

    @Transactional
    public Upload upload(ActorContext actor, UUID salonId, MultipartFile file, String purpose) {
        actor.requireSalon(salonId);
        String normalizedPurpose = normalizePurpose(purpose);
        actor.require("LOGO".equals(normalizedPurpose) ? "modify_business_settings" : "manage_services");
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Choose an image file");
        if (file.getSize() > maxBytes) throw new IllegalArgumentException("Image is larger than the salon upload limit");
        String contentType = normalizeContentType(file.getContentType());
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException error) {
            throw new IllegalArgumentException("Image could not be read", error);
        }
        if (data.length == 0 || data.length > maxBytes) throw new IllegalArgumentException("Image size is invalid");
        String detected = detectType(data);
        if (detected == null || !detected.equals(contentType)) {
            throw new IllegalArgumentException("Image type does not match its file contents");
        }
        MediaAsset saved = assets.save(new MediaAsset(salonId, normalizedPurpose, contentType, data));
        return new Upload(saved.getId(), "/api/public/media/" + saved.getId(), saved.getContentType(), saved.getFileSize());
    }

    @Transactional(readOnly = true)
    public MediaAsset publicAsset(UUID id) {
        if (id == null) throw new IllegalArgumentException("Media id is required");
        return assets.findById(id).orElseThrow(() -> new WalletService.NotFoundException("Media asset not found"));
    }

    private static String normalizePurpose(String value) {
        String purpose = String.valueOf(value == null ? "" : value).trim().toUpperCase(Locale.ROOT);
        if (!purpose.equals("LOGO") && !purpose.equals("HAIRCUT_STYLE")) {
            throw new IllegalArgumentException("Media purpose must be LOGO or HAIRCUT_STYLE");
        }
        return purpose;
    }

    private static String normalizeContentType(String value) {
        String type = String.valueOf(value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(type)) throw new IllegalArgumentException("Only JPEG, PNG, GIF or WEBP images are allowed");
        return type;
    }

    private static String detectType(byte[] data) {
        if (data.length >= 3 && (data[0] & 0xff) == 0xff && (data[1] & 0xff) == 0xd8 && (data[2] & 0xff) == 0xff) return "image/jpeg";
        if (data.length >= 8 && (data[0] & 0xff) == 0x89 && data[1] == 0x50 && data[2] == 0x4e && data[3] == 0x47 && data[4] == 0x0d && data[5] == 0x0a && data[6] == 0x1a && data[7] == 0x0a) return "image/png";
        if (data.length >= 6 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F' && data[3] == '8' && (data[4] == '7' || data[4] == '9') && data[5] == 'a') return "image/gif";
        if (data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F' && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') return "image/webp";
        return null;
    }

    public record Upload(UUID id, String uri, String contentType, long sizeBytes) {}
}
