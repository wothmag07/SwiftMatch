package com.swiftmatch.api.rider;

import com.swiftmatch.api.error.RiderNotFoundException;
import com.swiftmatch.common.rider.CreateRiderRequest;
import com.swiftmatch.common.rider.RiderResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Rider CRUD. Riders are anonymous and ephemeral; creation is
 * the only mutation.
 */
@Service
public class RiderService {

    private static final String[] ADJECTIVES =
            {"Swift", "Bold", "Quiet", "Warm", "Bright", "Calm", "Quick", "Steady"};
    private static final String[] ANIMALS =
            {"Fox", "Hawk", "Otter", "Lynx", "Wren", "Heron", "Panda", "Seal"};

    private final RiderRepository repository;

    public RiderService(RiderRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public RiderResponse create(CreateRiderRequest request) {
        String name = (request == null || request.name() == null || request.name().isBlank())
                ? generateName()
                : request.name();
        String phone = request == null ? null : request.phone();
        RiderEntity entity = new RiderEntity(UUID.randomUUID(), name, phone);
        repository.save(entity);
        return toDto(entity);
    }

    @Transactional(readOnly = true)
    public void requireExisting(UUID riderId) {
        if (!repository.existsById(riderId)) {
            throw new RiderNotFoundException(riderId);
        }
    }

    private static String generateName() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return ADJECTIVES[r.nextInt(ADJECTIVES.length)] + ANIMALS[r.nextInt(ANIMALS.length)];
    }

    private static RiderResponse toDto(RiderEntity e) {
        return new RiderResponse(e.getId(), e.getName(), e.getPhone(), e.getCreatedAt());
    }
}
