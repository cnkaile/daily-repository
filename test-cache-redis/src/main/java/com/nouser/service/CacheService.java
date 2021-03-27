package com.nouser.service;

import com.nouser.config.annotations.UseAopCache;
import com.nouser.enums.CacheTimes;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class CacheService {
    private static final Logger logger = LoggerFactory.getLogger(CacheService.class);
    private static Integer BASE_CACHE_SIGN = 0;
    private static final String CACHE_KEY = "BASE_CACHE:";

    private final StringRedisTemplate stringRedisTemplate;

    @Autowired
    public CacheService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

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

    public String incr(String name){
        return String.valueOf(++BASE_CACHE_SIGN);
    }

    @UseAopCache(customKey =  "#root.targetClass.getName() + ':' + #root.methodName + '#' + #name", timeOut = 10 * 1000)
    public String decr(String name){
        return String.valueOf(--BASE_CACHE_SIGN) + " - " + name;
    }

    @Cacheable(value = CacheTimes.D1, key = "#root.methodName", unless = "#result == null || #result.size() < 1", condition = "#skip != null")
    public List<String> getList(String skip) {
        return Arrays.stream(UUID.randomUUID().toString().split("-")).collect(Collectors.toList());
    }


    @CachePut(value = CacheTimes.D1, key = "#root.targetClass.getMethod('getList', #root.targetClass).name", unless = "#result == null || #result.size() < 1", condition = "#condition" )
    public List<String> setListCache(List<String> list, boolean condition){
        if(list != null || list.size() > 0){
            return list;
        }
        return Arrays.stream(UUID.randomUUID().toString().split("-")).collect(Collectors.toList());
    }


    public String myKeyGenerator(){
        return "myKey01";
    }
}
