package com.swiftmatch.api.rider;

import com.swiftmatch.api.error.RiderNotFoundException;
import com.swiftmatch.common.rider.CreateRiderRequest;
import com.swiftmatch.common.rider.RiderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiderServiceTest {

    private InMemoryRiderRepository repo;
    private RiderService service;

    @BeforeEach
    void setUp() {
        repo = new InMemoryRiderRepository();
        service = new RiderService(repo);
    }

    @Test
    void create_with_explicit_name_persists_as_given() {
        RiderResponse r = service.create(new CreateRiderRequest("Rhea", "+14155550000"));
        assertThat(r.name()).isEqualTo("Rhea");
        assertThat(r.phone()).isEqualTo("+14155550000");
        assertThat(repo.findById(r.id())).isPresent();
    }

    @Test
    void create_with_null_request_generates_anonymous_name() {
        RiderResponse r = service.create(null);
        assertThat(r.name()).isNotBlank();
        assertThat(r.id()).isNotNull();
    }

    @Test
    void create_with_blank_name_generates_anonymous_name() {
        RiderResponse r = service.create(new CreateRiderRequest("", null));
        assertThat(r.name()).isNotBlank();
    }

    @Test
    void requireExisting_throws_for_unknown_id() {
        UUID unknown = UUID.randomUUID();
        assertThatThrownBy(() -> service.requireExisting(unknown))
                .isInstanceOf(RiderNotFoundException.class);
    }

    @Test
    void requireExisting_passes_for_known_id() {
        RiderResponse r = service.create(new CreateRiderRequest("Rico", null));
        service.requireExisting(r.id());
    }

    /** Minimal in-memory stub — lets the service run without Spring Data JPA. */
    private static class InMemoryRiderRepository implements RiderRepository {
        private final Map<UUID, RiderEntity> store = new HashMap<>();

        @Override
        public <S extends RiderEntity> S save(S entity) {
            // trigger @PrePersist equivalent
            try {
                var m = RiderEntity.class.getDeclaredMethod("onCreate");
                m.setAccessible(true);
                m.invoke(entity);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
            store.put(entity.getId(), entity);
            return entity;
        }

        @Override public boolean existsById(UUID id) { return store.containsKey(id); }
        @Override public Optional<RiderEntity> findById(UUID id) { return Optional.ofNullable(store.get(id)); }
        @Override public long count() { return store.size(); }
        @Override public java.util.List<RiderEntity> findAll() { return java.util.List.copyOf(store.values()); }

        // --- unused JpaRepository methods ---
        @Override public java.util.List<RiderEntity> findAll(org.springframework.data.domain.Sort s) { throw u(); }
        @Override public org.springframework.data.domain.Page<RiderEntity> findAll(org.springframework.data.domain.Pageable p) { throw u(); }
        @Override public <S extends RiderEntity> java.util.List<S> saveAll(Iterable<S> es) {
            return Stream.of(es).flatMap(i -> java.util.stream.StreamSupport.stream(i.spliterator(), false))
                    .map(this::save).collect(Collectors.toList());
        }
        @Override public <S extends RiderEntity> java.util.List<S> saveAllAndFlush(Iterable<S> es) { return saveAll(es); }
        @Override public <S extends RiderEntity> S saveAndFlush(S entity) { return save(entity); }
        @Override public void flush() { }
        @Override public void deleteAllInBatch() { store.clear(); }
        @Override public void deleteAllByIdInBatch(Iterable<UUID> ids) { ids.forEach(store::remove); }
        @Override public void deleteAllInBatch(Iterable<RiderEntity> es) { es.forEach(e -> store.remove(e.getId())); }
        @Override public RiderEntity getOne(UUID id) { return store.get(id); }
        @Override public RiderEntity getById(UUID id) { return store.get(id); }
        @Override public RiderEntity getReferenceById(UUID id) { return store.get(id); }
        @Override public java.util.List<RiderEntity> findAllById(Iterable<UUID> ids) { throw u(); }
        @Override public void deleteById(UUID id) { store.remove(id); }
        @Override public void delete(RiderEntity e) { store.remove(e.getId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> ids) { ids.forEach(store::remove); }
        @Override public void deleteAll(Iterable<? extends RiderEntity> es) { es.forEach(e -> store.remove(e.getId())); }
        @Override public void deleteAll() { store.clear(); }
        @Override public <S extends RiderEntity> Optional<S> findOne(org.springframework.data.domain.Example<S> ex) { throw u(); }
        @Override public <S extends RiderEntity> java.util.List<S> findAll(org.springframework.data.domain.Example<S> ex) { throw u(); }
        @Override public <S extends RiderEntity> java.util.List<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Sort s) { throw u(); }
        @Override public <S extends RiderEntity> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Pageable p) { throw u(); }
        @Override public <S extends RiderEntity> long count(org.springframework.data.domain.Example<S> ex) { throw u(); }
        @Override public <S extends RiderEntity> boolean exists(org.springframework.data.domain.Example<S> ex) { throw u(); }
        @Override public <S extends RiderEntity, R> R findBy(org.springframework.data.domain.Example<S> ex, Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> f) { throw u(); }
        private static UnsupportedOperationException u() { return new UnsupportedOperationException("not used in tests"); }
    }
}
