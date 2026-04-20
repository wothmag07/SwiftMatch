package com.swiftmatch.api.driver;

import com.swiftmatch.common.driver.CreateDriverRequest;
import com.swiftmatch.common.driver.DriverResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/v1/drivers")
public class DriverController {

    private final DriverService service;

    public DriverController(DriverService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<DriverResponse> create(@Valid @RequestBody CreateDriverRequest request) {
        DriverResponse response = service.create(request);
        return ResponseEntity
                .created(URI.create("/v1/drivers/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    public DriverResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/online")
    public DriverResponse goOnline(@PathVariable UUID id) {
        return service.goOnline(id);
    }

    @PostMapping("/{id}/offline")
    public DriverResponse goOffline(@PathVariable UUID id) {
        return service.goOffline(id);
    }
}
