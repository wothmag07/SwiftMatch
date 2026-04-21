package com.swiftmatch.api.ride;

import com.swiftmatch.common.ride.Coord;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Redis-backed {@link DriverGeoIndex}. Emits {@code GEOSEARCH drivers:active FROMLONLAT
 * … BYRADIUS {km} ASC COUNT 10 WITHCOORD WITHDIST}.
 */
@Component
public class RedisDriverGeoIndex implements DriverGeoIndex {

    static final String ACTIVE_GEO_KEY = "drivers:active";
    private static final long GEO_COUNT = 10L;

    private final StringRedisTemplate redis;

    public RedisDriverGeoIndex(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public List<Candidate> candidatesNear(Coord pickup, int radiusKm) {
        Circle area = new Circle(new Point(pickup.lng(), pickup.lat()),
                new Distance(radiusKm, Metrics.KILOMETERS));
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs
                .newGeoRadiusArgs()
                .includeCoordinates()
                .includeDistance()
                .sortAscending()
                .limit(GEO_COUNT);
        GeoResults<GeoLocation<String>> results = redis.opsForGeo().radius(ACTIVE_GEO_KEY, area, args);
        if (results == null) return List.of();

        List<Candidate> out = new ArrayList<>(results.getContent().size());
        for (GeoResult<GeoLocation<String>> r : results) {
            GeoLocation<String> loc = r.getContent();
            Point p = loc.getPoint();
            out.add(new Candidate(loc.getName(), p.getY(), p.getX()));
        }
        return out;
    }
}
