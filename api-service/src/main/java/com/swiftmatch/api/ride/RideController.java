package com.swiftmatch.api.ride;

import com.swiftmatch.common.ride.CreateRideRequest;
import com.swiftmatch.common.ride.RideAssignmentResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/rides")
public class RideController {

    private final RideService service;

    public RideController(RideService service) {
        this.service = service;
    }

    @PostMapping
    public RideAssignmentResponse request(@Valid @RequestBody CreateRideRequest request) {
        return service.request(request);
    }
}
