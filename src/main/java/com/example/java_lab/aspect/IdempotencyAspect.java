package com.example.java_lab.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Aspect
@Component
public class IdempotencyAspect {

    private final Map<String, Object> responseCache = new ConcurrentHashMap<>();

    @Around("@annotation(com.example.java_lab.annotation.IdempotentRequest)")
    public Object enforceIdempotency(ProceedingJoinPoint joinPoint) throws Throwable {
        String key = extractIdempotencyKey(joinPoint.getArgs());

        if (key == null || key.isBlank()) {
            return joinPoint.proceed();
        }

        if (responseCache.containsKey(key)) {
            log.info("[AOP Idempotency] Повторний запит з ключем '{}'. Повернення закешованого результату без виконання методу.", key);
            return responseCache.get(key);
        }

        Object result = joinPoint.proceed();
        if (result != null) {
            responseCache.put(key, result);
            log.info("[AOP Idempotency] Збережено результат для ключа '{}'", key);
        }
        return result;
    }

    private String extractIdempotencyKey(Object[] args) {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            String headerKey = attributes.getRequest().getHeader("Idempotency-Key");
            if (headerKey != null && !headerKey.isBlank()) {
                return headerKey.trim();
            }
        }
        for (Object arg : args) {
            if (arg instanceof String strArg && !strArg.isBlank()) {
                return strArg.trim();
            }
        }
        return null;
    }

    public void clearCache() {
        responseCache.clear();
    }
}