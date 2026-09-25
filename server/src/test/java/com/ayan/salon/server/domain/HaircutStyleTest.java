package com.ayan.salon.server.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HaircutStyleTest {
    private final UUID salon = UUID.randomUUID();

    @Test
    void styleKeepsBoundedPhotoMetadataAndCanBeArchived() {
        HaircutStyle style = new HaircutStyle(salon, "  Fade  ", "https://cdn.example/fade.jpg",
                1_200, " Clean fade ", 2);

        assertEquals("Fade", style.getName());
        assertEquals("https://cdn.example/fade.jpg", style.getPhotoUri());
        assertEquals("Fade", style.getPhotoAltText());
        assertEquals(1_200, style.getPriceMinor());
        assertEquals(2, style.getDisplayOrder());
        assertTrue(style.isActive());

        style.updatePhoto("https://cdn.example/fade-v2.jpg", "Low fade style");
        style.deactivate();
        assertEquals("https://cdn.example/fade-v2.jpg", style.getPhotoUrl());
        assertEquals("Low fade style", style.getPhotoAltText());
        assertFalse(style.isActive());
    }

    @Test
    void styleRejectsMissingPhotoAndNegativePrice() {
        assertThrows(IllegalArgumentException.class,
                () -> new HaircutStyle(salon, "Fade", "", 1_200, null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new HaircutStyle(salon, "Fade", "https://cdn.example/fade.jpg", -1, null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new HaircutStyle(salon, "Fade", "javascript:alert(1)", 1_200, null, 0));
    }
}
