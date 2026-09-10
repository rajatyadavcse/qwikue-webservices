package com.kitchen.order.aspect;

import com.kitchen.order.annotation.Idempotent;
import com.kitchen.order.dto.IdempotencyRecord;
import com.kitchen.order.service.IdempotencyService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Map;

@Aspect
@Component
public class IdempotencyAspect {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyAspect.class);

    @Autowired
    private IdempotencyService idempotencyService;

    @Around("@annotation(com.kitchen.order.annotation.Idempotent)")
    public Object handleIdempotency(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Idempotent idempotentAnnotation = method.getAnnotation(Idempotent.class);

        String headerName = idempotentAnnotation.headerName();
        String idempotencyKey = request.getHeader(headerName);

        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            if (idempotentAnnotation.required()) {
                log.warn("Missing required idempotency key header: {}", headerName);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Missing required HTTP header: " + headerName));
            }
            return joinPoint.proceed();
        }

        idempotencyKey = idempotencyKey.trim();
        log.debug("Processing request with Idempotency Key: {}", idempotencyKey);

        // 1. Check if response is already cached
        IdempotencyRecord cachedRecord = idempotencyService.get(idempotencyKey);
        if (cachedRecord != null && cachedRecord.getState() == IdempotencyRecord.State.COMPLETED) {
            log.info("Returning cached response for Idempotency Key: {}", idempotencyKey);
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Cache-Lookup", "HIT");
            headers.add("Idempotent-Replay", "true");

            if (cachedRecord.getResponseBody() instanceof ResponseEntity<?>) {
                ResponseEntity<?> entity = (ResponseEntity<?>) cachedRecord.getResponseBody();
                return ResponseEntity.status(entity.getStatusCode())
                        .headers(headers)
                        .headers(entity.getHeaders())
                        .body(entity.getBody());
            }

            HttpStatus status = HttpStatus.resolve(cachedRecord.getStatusCode());
            return ResponseEntity.status(status != null ? status : HttpStatus.OK)
                    .headers(headers)
                    .body(cachedRecord.getResponseBody());
        }

        // 2. Try acquiring lock for in-flight processing
        boolean acquired = idempotencyService.acquireLock(idempotencyKey);
        if (!acquired) {
            log.warn("Concurrent request detected for Idempotency Key: {}", idempotencyKey);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", "Conflict",
                            "message", "A request with this idempotency key is currently in progress. Please wait."
                    ));
        }

        try {
            Object result = joinPoint.proceed();

            int statusCode = HttpStatus.OK.value();
            Object body = result;

            if (result instanceof ResponseEntity<?>) {
                ResponseEntity<?> responseEntity = (ResponseEntity<?>) result;
                statusCode = responseEntity.getStatusCode().value();
                body = responseEntity;
            }

            idempotencyService.complete(idempotencyKey, statusCode, body);
            log.debug("Saved completion state for Idempotency Key: {}", idempotencyKey);
            return result;
        } catch (Throwable t) {
            log.error("Request failed with Idempotency Key {}: {}", idempotencyKey, t.getMessage());
            idempotencyService.fail(idempotencyKey);
            throw t;
        }
    }
}
