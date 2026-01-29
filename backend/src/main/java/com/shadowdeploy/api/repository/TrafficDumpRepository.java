package com.shadowdeploy.api.repository;

import com.shadowdeploy.api.entity.TrafficDump;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrafficDumpRepository extends JpaRepository<TrafficDump, Long> {
}
