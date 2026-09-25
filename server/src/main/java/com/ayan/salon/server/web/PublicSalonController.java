package com.ayan.salon.server.web;

import com.ayan.salon.server.domain.*;
import com.ayan.salon.server.domain.repository.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Read-only catalog/branding data needed before a customer has a session. */
@RestController
@RequestMapping("/api/public/salons/{salonId}")
public class PublicSalonController {
    private final SalonSettingsRepository settings;
    private final ServiceOfferingRepository services;
    private final HaircutStyleRepository styles;
    private final AddOnRepository addOns;

    public PublicSalonController(SalonSettingsRepository settings, ServiceOfferingRepository services,
                                 HaircutStyleRepository styles, AddOnRepository addOns) {
        this.settings = settings; this.services = services; this.styles = styles; this.addOns = addOns;
    }

    @GetMapping("/catalog")
    public Catalog catalog(@PathVariable UUID salonId) {
        SalonSettings salon = settings.findById(salonId).orElseThrow(() -> new IllegalArgumentException("Salon not found"));
        return new Catalog(new Branding(salon.getName(), salon.getAddress(), salon.getPhone(), salon.getPrimaryColor(), salon.getLogoUri()),
                services.findBySalonIdAndActiveTrueOrderByName(salonId),
                styles.findBySalonIdAndActiveTrueOrderByDisplayOrderAscNameAsc(salonId),
                addOns.findBySalonIdAndActiveTrueOrderByName(salonId));
    }

    @GetMapping("/branding")
    public Branding branding(@PathVariable UUID salonId) {
        SalonSettings salon = settings.findById(salonId).orElseThrow(() -> new IllegalArgumentException("Salon not found"));
        return new Branding(salon.getName(), salon.getAddress(), salon.getPhone(), salon.getPrimaryColor(), salon.getLogoUri());
    }

    public record Branding(String name, String address, String phone, String primaryColor, String logoUri) {}
    public record Catalog(Branding branding, List<ServiceOffering> services, List<HaircutStyle> haircutStyles, List<AddOn> addOns) {}
}
