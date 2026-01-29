package com.shadowdeploy.api.repository;

import com.shadowdeploy.api.entity.ShadowSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShadowSummaryRepository extends JpaRepository<ShadowSummaryEntity, Long> {
    Optional<ShadowSummaryEntity> findTopByOrderByGeneratedAtDesc();
}
