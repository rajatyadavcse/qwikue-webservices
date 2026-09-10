package com.kitchen.order.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kitchen.order.dto.IdempotencyRecord;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class IdempotencyService {

    private final Cache<String, IdempotencyRecord> idempotencyCache;
    private final ConcurrentHashMap<String, Boolean> inFlightKeys = new ConcurrentHashMap<>();

    public IdempotencyService() {
        this.idempotencyCache = Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .maximumSize(10_000)
                .build();
    }

    /**
     * Attempts to acquire in-flight processing lock for the key.
     * Returns true if lock was acquired by current thread.
     * Returns false if key is already in progress by another thread.
     */
    public boolean acquireLock(String key) {
        return inFlightKeys.putIfAbsent(key, Boolean.TRUE) == null;
    }

    public void releaseLock(String key) {
        inFlightKeys.remove(key);
    }

    public IdempotencyRecord get(String key) {
        return idempotencyCache.getIfPresent(key);
    }

    public void complete(String key, int statusCode, Object responseBody) {
        IdempotencyRecord record = new IdempotencyRecord(key);
        record.setState(IdempotencyRecord.State.COMPLETED);
        record.setStatusCode(statusCode);
        record.setResponseBody(responseBody);
        idempotencyCache.put(key, record);
        inFlightKeys.remove(key);
    }

    public void fail(String key) {
        idempotencyCache.invalidate(key);
        inFlightKeys.remove(key);
    }
}
