package com.assetshield.notification.repo;

import com.assetshield.notification.domain.TipTemplate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TipTemplateRepository extends JpaRepository<TipTemplate, UUID> {

    List<TipTemplate> findByActiveTrueAndLanguage(String language);
}
