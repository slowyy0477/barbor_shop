package com.ayan.salon.server.web;

import com.ayan.salon.server.domain.MediaAsset;
import com.ayan.salon.server.service.ActorContext;
import com.ayan.salon.server.service.AuthenticatedActorResolver;
import com.ayan.salon.server.service.MediaAssetService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
public class MediaController {
    private final MediaAssetService media;
    private final AuthenticatedActorResolver actors;

    public MediaController(MediaAssetService media, AuthenticatedActorResolver actors) {
        this.media = media;
        this.actors = actors;
    }

    @PostMapping(value = "/salons/{salonId}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MediaAssetService.Upload upload(@PathVariable UUID salonId,
                                           @RequestPart("file") MultipartFile file,
                                           @RequestParam("purpose") String purpose) {
        ActorContext actor = actors.require();
        return media.upload(actor, salonId, file, purpose);
    }

    @GetMapping("/public/media/{id}")
    public ResponseEntity<byte[]> publicAsset(@PathVariable UUID id) {
        MediaAsset asset = media.publicAsset(id);
        MediaType type = MediaType.parseMediaType(asset.getContentType());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(type);
        headers.setContentLength(asset.getFileSize());
        headers.setContentDisposition(ContentDisposition.inline().filename("salon-image").build());
        headers.setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());
        headers.set("X-Content-Type-Options", "nosniff");
        return new ResponseEntity<>(asset.getData(), headers, org.springframework.http.HttpStatus.OK);
    }
}
