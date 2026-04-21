package com.swiftmatch.api.rider;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RiderRepository extends JpaRepository<RiderEntity, UUID> {
}
