package com.swiftmatch.api.location;

import com.swiftmatch.common.location.LocationUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/drivers/{id}/location")
public class LocationController {

    private final LocationService service;

    public LocationController(LocationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Void> update(@PathVariable UUID id,
                                       @Valid @RequestBody LocationUpdateRequest request) {
        service.ingest(id, request);
        return ResponseEntity.accepted().build();
    }
}
