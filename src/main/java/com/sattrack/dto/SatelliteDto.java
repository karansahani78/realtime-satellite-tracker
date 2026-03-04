package com.sattrack.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

public class SatelliteDto {

    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SatelliteSummary(
            Long id,
            String noradId,
            String name,
            String category,
            String description,
            String countryCode,
            boolean active,
            Instant tleEpoch
    ) {}

    @Builder
    public record SatellitePosition(
            String noradId,
            String name,
            Instant timestamp,
            double latitudeDeg,
            double longitudeDeg,
            double altitudeKm,
            double speedKmPerS,
            double velocityKmPerS,
            double orbitalPeriodMinutes,
            @JsonInclude(JsonInclude.Include.NON_NULL) LookAnglesDto lookAngles
    ) {}

    @Builder
    public record LookAnglesDto(
            double azimuthDeg,
            double elevationDeg,
            double rangeKm,
            boolean visible   // elevation > 0
    ) {}

    @Builder
    public record TrackPoint(
            Instant timestamp,
            double latitudeDeg,
            double longitudeDeg,
            double altitudeKm,
            double speedKmPerS
    ) {}

    @Builder
    public record TrackResponse(
            String noradId,
            String name,
            Instant startTime,
            Instant endTime,
            int intervalSeconds,
            List<TrackPoint> points
    ) {}

    @Builder
    public record PredictionResponse(
            String noradId,
            String name,
            Instant predictedAt,
            int minutesAhead,
            SatellitePosition position
    ) {}

    @Builder
    public record TleInfo(
            String noradId,
            String line1,
            String line2,
            Instant epoch,
            String source,
            Instant fetchedAt
    ) {}
}
