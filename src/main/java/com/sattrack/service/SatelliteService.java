package com.sattrack.service;

import com.sattrack.dto.SatelliteDto;
import com.sattrack.entity.Satellite;
import com.sattrack.entity.TleRecord;
import com.sattrack.exception.SatelliteNotFoundException;
import com.sattrack.repository.SatelliteRepository;
import com.sattrack.repository.TleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SatelliteService {

    private final SatelliteRepository satelliteRepository;
    private final TleRepository tleRepository;

    @Cacheable(value = "satellites", key = "'page:' + #page + ':' + #size + ':' + #category")
    public Page<SatelliteDto.SatelliteSummary> listSatellites(
            int page, int size, String category) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by("name"));
        Page<Satellite> entities = (category != null && !category.isBlank())
                ? satelliteRepository.findByCategoryAndActiveTrue(category, pageable)
                : satelliteRepository.findByActiveTrue(pageable);

        return entities.map(this::toSummary);
    }

    @Cacheable(value = "satelliteSearch", key = "#query + ':' + #page")
    public Page<SatelliteDto.SatelliteSummary> search(String query, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("name"));
        return satelliteRepository.search(query, pageable).map(this::toSummary);
    }

    public SatelliteDto.SatelliteSummary getSatelliteByNoradId(String noradId) {
        return satelliteRepository.findByNoradId(noradId)
                .map(this::toSummary)
                .orElseThrow(() -> new SatelliteNotFoundException(noradId));
    }

    public List<String> getCategories() {
        return satelliteRepository.findDistinctCategories();
    }

    public SatelliteDto.TleInfo getLatestTle(String noradId) {
        return tleRepository.findLatestByNoradId(noradId)
                .map(t -> SatelliteDto.TleInfo.builder()
                        .noradId(t.getNoradId())
                        .line1(t.getLine1())
                        .line2(t.getLine2())
                        .epoch(t.getEpoch())
                        .source(t.getSource())
                        .fetchedAt(t.getFetchedAt())
                        .build())
                .orElseThrow(() -> new SatelliteNotFoundException(
                        "No TLE found for NORAD ID: " + noradId));
    }

    private SatelliteDto.SatelliteSummary toSummary(Satellite s) {
        // Get latest TLE epoch without triggering full collection load
        TleRecord latestTle = tleRepository.findLatestByNoradId(s.getNoradId()).orElse(null);

        return SatelliteDto.SatelliteSummary.builder()
                .id(s.getId())
                .noradId(s.getNoradId())
                .name(s.getName())
                .category(s.getCategory())
                .description(s.getDescription())
                .countryCode(s.getCountryCode())
                .active(s.isActive())
                .tleEpoch(latestTle != null ? latestTle.getEpoch() : null)
                .build();
    }
}
