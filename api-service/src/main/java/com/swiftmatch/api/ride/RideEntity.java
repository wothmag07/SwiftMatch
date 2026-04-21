package com.swiftmatch.api.ride;

import com.swiftmatch.common.ride.RideStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rides")
public class RideEntity {

    @Id
    private UUID id;

    @Column(name = "rider_id", nullable = false)
    private UUID riderId;

    @Column(name = "driver_id")
    private UUID driverId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RideStatus status;

    @Column(name = "pickup_lat", nullable = false)
    private double pickupLat;

    @Column(name = "pickup_lng", nullable = false)
    private double pickupLng;

    @Column(name = "dropoff_lat", nullable = false)
    private double dropoffLat;

    @Column(name = "dropoff_lng", nullable = false)
    private double dropoffLng;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failure_reason", length = 80)
    private String failureReason;

    protected RideEntity() {
    }

    public RideEntity(UUID id, UUID riderId,
                      double pickupLat, double pickupLng,
                      double dropoffLat, double dropoffLng) {
        this.id = id;
        this.riderId = riderId;
        this.status = RideStatus.PENDING;
        this.pickupLat = pickupLat;
        this.pickupLng = pickupLng;
        this.dropoffLat = dropoffLat;
        this.dropoffLng = dropoffLng;
    }

    @PrePersist
    void onCreate() {
        if (requestedAt == null) requestedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getRiderId() { return riderId; }
    public UUID getDriverId() { return driverId; }
    public RideStatus getStatus() { return status; }
    public double getPickupLat() { return pickupLat; }
    public double getPickupLng() { return pickupLng; }
    public double getDropoffLat() { return dropoffLat; }
    public double getDropoffLng() { return dropoffLng; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getAssignedAt() { return assignedAt; }

    public void assignTo(UUID driverId, Instant assignedAt) {
        this.driverId = driverId;
        this.status = RideStatus.ASSIGNED;
        this.assignedAt = assignedAt;
    }

    public void markNoDriverFound() {
        this.status = RideStatus.NO_DRIVER_FOUND;
    }
}
