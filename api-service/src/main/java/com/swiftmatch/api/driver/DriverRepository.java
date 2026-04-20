package com.swiftmatch.api.driver;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DriverRepository extends JpaRepository<DriverEntity, UUID> {
}
