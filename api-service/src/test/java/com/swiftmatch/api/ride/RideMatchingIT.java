package com.swiftmatch.api.ride;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftmatch.api.driver.DriverStatusRedisWriter;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the ride-matching pipeline end-to-end against the live compose stack
 * (Postgres + Redis + Kafka on host ports 5433 / 6379 / 9094).
 *
 * <p>Opt-in via {@code RUN_IT=true}; requires {@code docker compose up -d
 * postgres redis kafka}. Covers both binding Gherkin scenarios from
 * the binding concurrency + SLA scenarios plus surrounding error paths.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@EnabledIfEnvironmentVariable(named = "RUN_IT", matches = "true")
class RideMatchingIT {

    @Autowired MockMvc mockMvc;
    @Autowired StringRedisTemplate redis;
    @Autowired DriverStatusRedisWriter driverStatus;
    @Autowired JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void cleanState() {
        jdbc.update("DELETE FROM rides");
        jdbc.update("DELETE FROM riders");
        jdbc.update("DELETE FROM drivers");
        redis.delete("drivers:active");
        // Leftover status/heartbeat keys from prior runs.
        redis.keys("driver:*").forEach(redis::delete);
        redis.keys("lock:driver:*").forEach(redis::delete);
    }

    @Test
    void tc_mat_sla_no_driver_found_within_8s_budget() throws Exception {
        UUID riderId = createRider("Rhea");

        long start = System.currentTimeMillis();
        mockMvc.perform(post("/v1/rides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rideBody(riderId, 37.7749, -122.4194, 37.7849, -122.4094)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.type").value(containsString("no-driver-found")));
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).as("matcher must return inside its 8s budget").isLessThan(8_500L);

        Integer noDriverRows = jdbc.queryForObject(
                "SELECT count(*) FROM rides WHERE rider_id=? AND status='NO_DRIVER_FOUND'",
                Integer.class, riderId);
        assertThat(noDriverRows).isEqualTo(1);
    }

    @Test
    void happy_path_single_driver_nearby_is_assigned() throws Exception {
        UUID riderId = createRider("Rhea");
        UUID driverId = createDriver("Alice", "Toyota Prius");
        placeAvailableDriver(driverId, 37.7750, -122.4200);

        MvcResult result = mockMvc.perform(post("/v1/rides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rideBody(riderId, 37.7749, -122.4194, 37.7849, -122.4094)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.driver.id").value(driverId.toString()))
                .andReturn();

        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("driver").get("etaSeconds").asInt()).isGreaterThanOrEqualTo(0);

        String redisStatus = redis.opsForValue().get("driver:" + driverId + ":status");
        assertThat(redisStatus).isEqualTo("ON_TRIP");

        String dbStatus = jdbc.queryForObject(
                "SELECT status FROM rides WHERE rider_id=?", String.class, riderId);
        assertThat(dbStatus).isEqualTo("ASSIGNED");
    }

    @Test
    void out_of_service_area_returns_400_problem_json() throws Exception {
        UUID riderId = createRider("Rhea");

        mockMvc.perform(post("/v1/rides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rideBody(riderId, 0.0, 0.0, 37.78, -122.41)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.type").value(containsString("out-of-service-area")))
                .andExpect(jsonPath("$.field").value("pickup"));
    }

    @Test
    void unknown_rider_returns_404_not_found() throws Exception {
        UUID unknown = UUID.randomUUID();

        mockMvc.perform(post("/v1/rides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rideBody(unknown, 37.7749, -122.4194, 37.7849, -122.4094)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.type").value(containsString("not-found")));
    }

    @Test
    void rider_has_active_ride_is_409() throws Exception {
        UUID riderId = createRider("Rhea");
        UUID driverId = createDriver("Alice", "Toyota Prius");
        placeAvailableDriver(driverId, 37.7750, -122.4200);

        mockMvc.perform(post("/v1/rides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rideBody(riderId, 37.7749, -122.4194, 37.7849, -122.4094)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/v1/rides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rideBody(riderId, 37.7749, -122.4194, 37.7849, -122.4094)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.type").value(containsString("rider-has-active-ride")));
    }

    /**
     * Concurrency invariant (binding scenario).
     * 1 online driver, 2 riders, both POSTs dispatched inside 1 ms — hammered 10×.
     * Invariants: exactly one rider gets the driver, the other gets 503 no-driver;
     * rides never contains two ON_TRIP rows for the same driver.
     */
    @Test
    void tc_mat_concurrency_never_double_assigns() throws Exception {
        int repeats = 10;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < repeats; i++) {
                jdbc.update("DELETE FROM rides");
                UUID driverId = createDriver("Alice", "Toyota Prius");
                placeAvailableDriver(driverId, 37.7750, -122.4200);
                UUID r1 = createRider("Rhea-" + i);
                UUID r2 = createRider("Rico-" + i);

                CountDownLatch gate = new CountDownLatch(1);
                AtomicInteger ok = new AtomicInteger();
                AtomicInteger noDriver = new AtomicInteger();
                List<Throwable> errs = new ArrayList<>();

                var f1 = pool.submit(() -> race(gate, r1, ok, noDriver, errs));
                var f2 = pool.submit(() -> race(gate, r2, ok, noDriver, errs));
                gate.countDown();
                f1.get(30, TimeUnit.SECONDS);
                f2.get(30, TimeUnit.SECONDS);

                assertThat(errs).as("run #" + i + " threw").isEmpty();
                assertThat(ok.get()).as("run #" + i + " successes").isEqualTo(1);
                assertThat(noDriver.get()).as("run #" + i + " no-driver").isEqualTo(1);

                Integer onTripRows = jdbc.queryForObject(
                        "SELECT count(*) FROM rides WHERE driver_id=? AND status IN ('ASSIGNED','ON_TRIP')",
                        Integer.class, driverId);
                assertThat(onTripRows).as("run #" + i + " active rides for driver").isEqualTo(1);

                // Tear down in FK order: rides → drivers. Riders stay — they're cheap and
                // have no FK targets pointing back.
                jdbc.update("DELETE FROM rides");
                jdbc.update("DELETE FROM drivers WHERE id=?", driverId);
                driverStatus.clear(driverId);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private void race(CountDownLatch gate, UUID riderId,
                      AtomicInteger ok, AtomicInteger noDriver, List<Throwable> errs) {
        try {
            gate.await();
            int sc = mockMvc.perform(post("/v1/rides")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(rideBody(riderId, 37.7749, -122.4194, 37.7849, -122.4094)))
                    .andReturn().getResponse().getStatus();
            if (sc == 200) ok.incrementAndGet();
            else if (sc == 503) noDriver.incrementAndGet();
            else synchronized (errs) { errs.add(new AssertionError("unexpected status " + sc)); }
        } catch (Throwable t) {
            synchronized (errs) { errs.add(t); }
        }
    }

    // --- helpers -----------------------------------------------------------

    private UUID createRider(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/riders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private UUID createDriver(String name, String vehicle) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"vehicle\":\"" + vehicle + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        UUID driverId = UUID.fromString(
                json.readTree(result.getResponse().getContentAsString()).get("id").asText());
        mockMvc.perform(post("/v1/drivers/{id}/online", driverId))
                .andExpect(status().isOk());
        return driverId;
    }

    private void placeAvailableDriver(UUID driverId, double lat, double lng) {
        // Directly seed Redis GEO (would normally be pushed in via indexer-service).
        redis.opsForGeo().add("drivers:active", new Point(lng, lat), driverId.toString());
        // driverStatus.markAvailable already ran via /online; ensure the TTL hasn't lapsed
        // between /online and here — in practice the IT runs in < 100 ms.
        Awaitility.await().atMost(Duration.ofSeconds(2)).until(() ->
                "AVAILABLE".equals(redis.opsForValue().get("driver:" + driverId + ":status")));
    }

    private String rideBody(UUID riderId, double pLat, double pLng, double dLat, double dLng) {
        return """
                {"riderId":"%s","pickup":{"lat":%s,"lng":%s},"dropoff":{"lat":%s,"lng":%s}}
                """.formatted(riderId, pLat, pLng, dLat, dLng);
    }
}
