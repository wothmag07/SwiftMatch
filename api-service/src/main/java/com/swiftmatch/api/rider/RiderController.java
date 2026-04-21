package com.swiftmatch.api.rider;

import com.swiftmatch.common.rider.CreateRiderRequest;
import com.swiftmatch.common.rider.RiderResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/v1/riders")
public class RiderController {

    private final RiderService service;

    public RiderController(RiderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<RiderResponse> create(@Valid @RequestBody(required = false) CreateRiderRequest request) {
        RiderResponse response = service.create(request);
        return ResponseEntity
                .created(URI.create("/v1/riders/" + response.id()))
                .body(response);
    }
}
