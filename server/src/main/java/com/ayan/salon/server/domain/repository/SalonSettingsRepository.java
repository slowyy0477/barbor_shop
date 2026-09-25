package com.ayan.salon.server.domain.repository;
import com.ayan.salon.server.domain.SalonSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface SalonSettingsRepository extends JpaRepository<SalonSettings, UUID> {}
