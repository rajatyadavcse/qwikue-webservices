package com.kitchen.order.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that an API endpoint should enforce idempotency based on an X-Idempotency-Key request header.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /**
     * Cache retention duration for idempotency records in seconds.
     * Default is 60 seconds.
     */
    long expireInSeconds() default 60;

    /**
     * Header name to inspect for idempotency key.
     * Default is "X-Idempotency-Key".
     */
    String headerName() default "X-Idempotency-Key";

    /**
     * Whether the idempotency key header is strictly required for the annotated endpoint.
     * If false (default), missing header proceeds without idempotency check.
     */
    boolean required() default false;
}
