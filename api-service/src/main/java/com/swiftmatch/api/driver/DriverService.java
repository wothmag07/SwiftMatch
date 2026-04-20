package com.swiftmatch.api.driver;

import com.swiftmatch.api.error.DriverNotFoundException;
import com.swiftmatch.common.driver.CreateDriverRequest;
import com.swiftmatch.common.driver.DriverResponse;
import com.swiftmatch.common.driver.DriverStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DriverService {

    private final DriverRepository repository;
    private final DriverStatusRedisWriter redis;

    public DriverService(DriverRepository repository, DriverStatusRedisWriter redis) {
        this.repository = repository;
        this.redis = redis;
    }

    @Transactional
    public DriverResponse create(CreateDriverRequest request) {
        DriverEntity entity = new DriverEntity(
                UUID.randomUUID(),
                request.name(),
                request.phone(),
                request.vehicle(),
                DriverStatus.OFFLINE
        );
        repository.save(entity);
        return toDto(entity);
    }

    @Transactional(readOnly = true)
    public DriverResponse get(UUID id) {
        return toDto(load(id));
    }

    @Transactional
    public DriverResponse goOnline(UUID id) {
        DriverEntity driver = load(id);
        DriverStatus target = DriverStatusTransitions.applyOnline(driver.getStatus());
        if (target != driver.getStatus()) {
            driver.setStatus(target);
            repository.save(driver);
        }
        if (target == DriverStatus.AVAILABLE) {
            redis.markAvailable(id);
        }
        return toDto(driver);
    }

    @Transactional
    public DriverResponse goOffline(UUID id) {
        DriverEntity driver = load(id);
        DriverStatus target = DriverStatusTransitions.applyOffline(driver.getStatus(), id);
        if (target != driver.getStatus()) {
            driver.setStatus(target);
            repository.save(driver);
        }
        redis.clear(id);
        return toDto(driver);
    }

    private DriverEntity load(UUID id) {
        return repository.findById(id).orElseThrow(() -> new DriverNotFoundException(id));
    }

    private static DriverResponse toDto(DriverEntity e) {
        return new DriverResponse(
                e.getId(),
                e.getName(),
                e.getPhone(),
                e.getVehicle(),
                e.getStatus(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
