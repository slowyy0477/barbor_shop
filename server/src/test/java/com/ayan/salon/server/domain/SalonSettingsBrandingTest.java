package com.ayan.salon.server.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SalonSettingsBrandingTest {
    @Test
    void brandingNormalizesColorAndSupportsLogoReplacement() {
        SalonSettings settings = new SalonSettings(UUID.randomUUID(), "Ayan", "address", "03101234567");
        settings.updateBranding(" #12abEF ", " https://cdn.example/logo.png ");

        assertEquals("#12ABEF", settings.getPrimaryColor());
        assertEquals("https://cdn.example/logo.png", settings.getLogoUri());
        assertEquals(settings.getLogoUri(), settings.getLogoUrl());

        settings.updateBranding(null, "");
        assertNull(settings.getLogoUri());
    }

    @Test
    void brandingRejectsInvalidColorAndMissingValues() {
        SalonSettings settings = new SalonSettings(UUID.randomUUID(), "Ayan", "address", "03101234567");
        assertThrows(IllegalArgumentException.class, () -> settings.updateBranding("red", null));
        assertThrows(IllegalArgumentException.class, () -> settings.updateBranding(null, null));
    }
}
