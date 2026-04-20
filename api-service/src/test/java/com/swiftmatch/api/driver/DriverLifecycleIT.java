package com.swiftmatch.api.driver;

import com.swiftmatch.common.driver.DriverStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the full driver lifecycle against the running Docker Compose infrastructure
 * (Postgres on localhost:5433, Redis on localhost:6379).
 *
 * <p>Expected pre-conditions:
 * <ul>
 *   <li>{@code docker compose up -d redis postgres} completed</li>
 *   <li>Schema is present (Flyway migration runs automatically on test boot)</li>
 * </ul>
 *
 * <p>Disabled by default so that vanilla {@code ./mvnw verify} doesn't fail in environments
 * without the compose stack. Opt in with {@code RUN_IT=true}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@EnabledIfEnvironmentVariable(named = "RUN_IT", matches = "true")
class DriverLifecycleIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    DriverRepository driverRepository;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void cleanSlate() {
        driverRepository.deleteAll();
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void create_driver_returns_201_and_persists_offline() throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Alice","vehicle":"Toyota Prius"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OFFLINE"))
                .andReturn();

        UUID id = UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
        assertThat(driverRepository.findById(id)).isPresent();
    }

    @Test
    void online_marks_available_in_db_and_redis_with_ttl() throws Exception {
        UUID id = createDriver();

        mockMvc.perform(post("/v1/drivers/{id}/online", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));

        assertThat(driverRepository.findById(id)).get()
                .extracting(DriverEntity::getStatus).isEqualTo(DriverStatus.AVAILABLE);

        String statusKey = DriverStatusRedisWriter.statusKey(id.toString());
        assertThat(redis.opsForValue().get(statusKey)).isEqualTo("AVAILABLE");
        Long ttl = redis.getExpire(statusKey);
        assertThat(ttl).isBetween(25L, 31L);
    }

    @Test
    void online_when_on_trip_is_noop() throws Exception {
        UUID id = createDriver();
        DriverEntity driver = driverRepository.findById(id).orElseThrow();
        driver.setStatus(DriverStatus.ON_TRIP);
        driverRepository.save(driver);

        mockMvc.perform(post("/v1/drivers/{id}/online", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_TRIP"));
    }

    @Test
    void offline_when_on_trip_returns_409_problem_json() throws Exception {
        UUID id = createDriver();
        DriverEntity driver = driverRepository.findById(id).orElseThrow();
        driver.setStatus(DriverStatus.ON_TRIP);
        driverRepository.save(driver);

        mockMvc.perform(post("/v1/drivers/{id}/offline", id))
                .andExpect(status().isConflict())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.containsString("driver-on-trip")))
                .andExpect(jsonPath("$.driverId").value(id.toString()));
    }

    @Test
    void offline_when_available_clears_redis() throws Exception {
        UUID id = createDriver();
        mockMvc.perform(post("/v1/drivers/{id}/online", id)).andExpect(status().isOk());

        mockMvc.perform(post("/v1/drivers/{id}/offline", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OFFLINE"));

        assertThat(redis.opsForValue().get(DriverStatusRedisWriter.statusKey(id.toString()))).isNull();
        assertThat(redis.opsForValue().get(DriverStatusRedisWriter.heartbeatKey(id.toString()))).isNull();
    }

    @Test
    void get_missing_driver_returns_404_problem_json() throws Exception {
        UUID missing = UUID.randomUUID();
        mockMvc.perform(get("/v1/drivers/{id}", missing))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.containsString("not-found")));
    }

    @Test
    void create_with_invalid_body_returns_400_problem_json() throws Exception {
        mockMvc.perform(post("/v1/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","vehicle":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.containsString("validation")));
    }

    private UUID createDriver() throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Alice","vehicle":"Toyota Prius"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }
}
