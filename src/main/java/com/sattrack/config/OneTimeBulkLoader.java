package com.sattrack.config;

import com.sattrack.service.TleFetcherService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OneTimeBulkLoader {

    private final TleFetcherService tleFetcherService;

    @EventListener(ApplicationReadyEvent.class)
    public void loadOnce() {
        tleFetcherService.refreshAllTles();
    }
}