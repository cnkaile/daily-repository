package com.nouser.config.advice;

import com.nouser.config.annotations.UseCache;
import com.nouser.utils.CacheUtils;
import com.nouser.utils.JsonUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

@RestControllerAdvice
public class ApiResponseBody implements ResponseBodyAdvice<Object> {
    private static final Logger logger = LoggerFactory.getLogger(ApiResponseBody.class);
    private final StringRedisTemplate redisTemplate;

    @Autowired
    public ApiResponseBody(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }


    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        logger.info("Into supports");
        Method method = returnType.getMethod();
        if(method != null){
            logger.info("Find This method use cache.");
            return method.isAnnotationPresent(UseCache.class);
        }
        return false;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        try{
            String value = JsonUtils.toGJsonString(body);
            String cacheKey = CacheUtils.keySerialization(request, returnType.getMethod());
            if(StringUtils.isNoneBlank(cacheKey)){
                redisTemplate.opsForValue().set(cacheKey, value, 60, TimeUnit.SECONDS);
            }
            logger.info("cache controller return content.");
        }catch (Exception e){
            logger.error("Cache Exception:{}", e.getMessage(), e);
        }
        return body;
    }
}
