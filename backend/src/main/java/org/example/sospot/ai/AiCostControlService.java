package org.example.sospot.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.example.sospot.ai.dto.AiChatResponse;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AiCostControlService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final AiUsageProperties properties;
    private final Cache<String, AiChatResponse> responseCache;
    private final ConcurrentHashMap<String, ArrayDeque<Instant>> requestsByClient =
        new ConcurrentHashMap<>();

    private LocalDate modelCallDate = LocalDate.now(SERVICE_ZONE);
    private int modelCallsToday;
    private Instant modelCallsBlockedUntil = Instant.EPOCH;

    public AiCostControlService(AiUsageProperties properties) {
        this.properties = properties;
        this.responseCache = Caffeine.newBuilder()
            .maximumSize(properties.cacheMaximumSize())
            .expireAfterWrite(properties.cacheTtl())
            .build();
    }

    public AiChatResponse getCached(String question) {
        return responseCache.getIfPresent(cacheKey(question));
    }

    public void cache(String question, AiChatResponse response) {
        responseCache.put(cacheKey(question), response);
    }

    public boolean allowQuestion(String clientKey) {
        Instant now = Instant.now();
        Instant cutoff = now.minusSeconds(60);
        ArrayDeque<Instant> requests = requestsByClient.computeIfAbsent(
            clientKey == null || clientKey.isBlank() ? "unknown" : clientKey,
            ignored -> new ArrayDeque<>()
        );
        synchronized (requests) {
            while (!requests.isEmpty() && requests.peekFirst().isBefore(cutoff)) {
                requests.removeFirst();
            }
            if (requests.size() >= properties.perIpRequestsPerMinute()) {
                return false;
            }
            requests.addLast(now);
            return true;
        }
    }

    public synchronized boolean allowModelCall() {
        if (Instant.now().isBefore(modelCallsBlockedUntil)) {
            return false;
        }
        LocalDate today = LocalDate.now(SERVICE_ZONE);
        if (!today.equals(modelCallDate)) {
            modelCallDate = today;
            modelCallsToday = 0;
        }
        if (modelCallsToday >= properties.dailyModelCalls()) {
            return false;
        }
        modelCallsToday++;
        return true;
    }

    public synchronized void blockModelCalls(Duration duration) {
        Instant candidate = Instant.now().plus(duration);
        if (candidate.isAfter(modelCallsBlockedUntil)) {
            modelCallsBlockedUntil = candidate;
        }
    }

    private String cacheKey(String question) {
        return question.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
