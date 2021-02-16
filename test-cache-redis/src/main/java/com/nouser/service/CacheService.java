package com.nouser.service;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class CacheService {
    private static final Logger logger = LoggerFactory.getLogger(CacheService.class);
    private static Integer BASE_CACHE_SIGN = 0;
    private static final String CACHE_KEY = "BASE_CACHE:";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public String baseCache(String name) {
        if(StringUtils.isBlank(name)){
            logger.error("Into BaseCache Service, Name is null.");
            return null;
        }
        logger.info("Into BaseCache Service, {}", name);
        String value = stringRedisTemplate.opsForValue().get(CACHE_KEY + "cache_sign");
        if(!StringUtils.isBlank(value)){
            return value;
        }
        else{
            value = String.valueOf(++BASE_CACHE_SIGN);
            stringRedisTemplate.opsForValue().set(CACHE_KEY + "cache_sign", value, 60, TimeUnit.SECONDS);
            return String.valueOf(BASE_CACHE_SIGN);
        }
    }

}
