package com.ayan.salon.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

/**
 * Small salon-owned image asset.  Images are deliberately bounded and stored
 * as bytes so a pilot can work without a second storage account.  The public
 * URL is an opaque UUID and does not expose the original filename.
 */
@Entity
@Table(name = "media_assets")
public class MediaAsset extends TenantEntity {
    @Column(name = "purpose", nullable = false, length = 32)
    private String purpose;
    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;
    @Column(name = "file_size", nullable = false)
    private long fileSize;
    // The controller reads the bytes after the repository call returns. Keep
    // this field materialized so the endpoint does not depend on Hibernate
    // bytecode enhancement or an open persistence context.
    @Column(name = "data", nullable = false)
    private byte[] data;
    @Version
    private long version;

    protected MediaAsset() {}

    public MediaAsset(UUID salonId, String purpose, String contentType, byte[] data) {
        super(salonId);
        if (purpose == null || purpose.isBlank() || purpose.trim().length() > 32) {
            throw new IllegalArgumentException("Media purpose is required");
        }
        if (contentType == null || contentType.isBlank() || contentType.trim().length() > 64) {
            throw new IllegalArgumentException("Media content type is required");
        }
        if (data == null || data.length == 0) throw new IllegalArgumentException("Media data is required");
        this.purpose = purpose.trim().toUpperCase(java.util.Locale.ROOT);
        this.contentType = contentType.trim().toLowerCase(java.util.Locale.ROOT);
        this.fileSize = data.length;
        this.data = data.clone();
    }

    public String getPurpose() { return purpose; }
    public String getContentType() { return contentType; }
    public long getFileSize() { return fileSize; }
    public byte[] getData() { return data == null ? new byte[0] : data.clone(); }
}
