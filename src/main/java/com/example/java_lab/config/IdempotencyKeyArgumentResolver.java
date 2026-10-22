package com.example.java_lab.config;

import com.example.java_lab.annotation.IdempotencyKey;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

public class IdempotencyKeyArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String HEADER_NAME = "Idempotency-Key";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(IdempotencyKey.class)
                && String.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        String key = webRequest.getHeader(HEADER_NAME);
        if (key != null && !key.isBlank()) {
            return key.trim();
        }
        return UUID.randomUUID().toString();
    }
}