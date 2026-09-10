package com.kitchen.order.service;

import com.kitchen.order.dto.IdempotencyRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IdempotencyServiceTest {

    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService();
    }

    @Test
    void testAcquireLock() {
        String key = "test-key-1";

        assertTrue(idempotencyService.acquireLock(key));
        assertFalse(idempotencyService.acquireLock(key), "Subsequent acquireLock with same key should fail");

        idempotencyService.releaseLock(key);
        assertTrue(idempotencyService.acquireLock(key), "Acquire lock should succeed after release");
    }

    @Test
    void testCompleteAndGet() {
        String key = "test-key-2";
        assertNull(idempotencyService.get(key));

        assertTrue(idempotencyService.acquireLock(key));
        idempotencyService.complete(key, 200, "Success Payload");

        IdempotencyRecord record = idempotencyService.get(key);
        assertNotNull(record);
        assertEquals(IdempotencyRecord.State.COMPLETED, record.getState());
        assertEquals(200, record.getStatusCode());
        assertEquals("Success Payload", record.getResponseBody());

        // Lock should be released upon completion
        assertTrue(idempotencyService.acquireLock(key));
    }

    @Test
    void testFail() {
        String key = "test-key-3";

        assertTrue(idempotencyService.acquireLock(key));
        idempotencyService.fail(key);

        assertNull(idempotencyService.get(key));
        assertTrue(idempotencyService.acquireLock(key));
    }
}
