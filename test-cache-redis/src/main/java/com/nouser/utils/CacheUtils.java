package com.nouser.utils;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServletServerHttpRequest;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Map;

public class CacheUtils {
    private static final Logger logger = LoggerFactory.getLogger(CacheUtils.class);

    public static String keySerialization(Object request, Method method) {
        logger.debug("This request class is {}", request.getClass().getName());
        if(request instanceof ServletServerHttpRequest){
            logger.info("This Request is ServletServerHttpRequest");
            HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
            return keySerialization(servletRequest, method).toString().replaceAll("[^a-zA-Z0-9_.#:]", "");
        }
        if(request instanceof HttpServletRequest){
            logger.info("This Request is HttpServletRequest");
            return keySerialization((HttpServletRequest) request, method).toString().replaceAll("[^a-zA-Z0-9_.#:]", "");
        }
        return null;
    }

    private static StringBuilder keySerialization(HttpServletRequest request, Method method){
        StringBuilder cacheKey = new StringBuilder("test:");
        String packageName = method.getDeclaringClass().getName();
        cacheKey.append(packageName).append(":");
        cacheKey.append(method.getName()).append("#");
        Map<String, String[]> parameterMap = request.getParameterMap();
        if (parameterMap != null && parameterMap.size() > 0) {
            for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
                cacheKey.append(entry.getKey()).append(".").append(StringUtils.join(entry.getValue(), "_")).append("#");
            }
        }
        logger.debug(cacheKey.toString());
        return cacheKey;
    }

    private CacheUtils() {
    }
}
