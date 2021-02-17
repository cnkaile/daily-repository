package com.nouser.utils;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.method.HandlerMethod;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Map;

public class CacheUtils {
    private static final Logger logger = LoggerFactory.getLogger(CacheUtils.class);

    public static String keySerialization(ServerHttpRequest request, Method method) {
        if(request instanceof ServletServerHttpRequest){
            logger.info("This Request is ServletServerHttpRequest");
            HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
            return keySerialization(servletRequest, method.getName()).toString();
        }
        if(request instanceof HttpServletRequest){
            logger.info("This Request is HttpServletRequest");
            return keySerialization((HttpServletRequest) request, method.getName()).toString();
        }
        return null;
    }

    public static String keySerialization(HttpServletRequest request, Method method) {
        return keySerialization(request, method.getName()).toString();
    }

    private static StringBuilder keySerialization(HttpServletRequest request, String methodName){
        StringBuilder cacheKey = new StringBuilder("test:");
        cacheKey.append(methodName).append(":");
        Map<String, String[]> parameterMap = request.getParameterMap();
        if (parameterMap != null && parameterMap.size() > 0) {
            for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
                cacheKey.append(entry.getKey()).append(StringUtils.join(entry.getValue(), "-")).append("&");
            }
        }
        return cacheKey;
    }

    private CacheUtils() {
    }
}
