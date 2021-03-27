package com.nouser.controller;

import com.nouser.config.annotations.UseCache;
import com.nouser.enums.CacheTimes;
import com.nouser.service.CacheAopService;
import com.nouser.service.CacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cache")
public class CacheController {
    private static final Logger logger = LoggerFactory.getLogger(CacheController.class);
    private final CacheService cacheService;
    private final CacheAopService cacheAopService;

    @Autowired
    public CacheController(CacheService cacheService, CacheAopService cacheAopService) {
        this.cacheService = cacheService;
        this.cacheAopService = cacheAopService;
    }

    @RequestMapping("/cacheAnnotation")
    public String cacheAnnotation(String name, String update){

//        String result = cacheService.cacheAnnotationUse(name, update);

        return "OK - ";
    }

    @Cacheable(value = CacheTimes.D1,
            key = "(#root.targetClass.getName() + ':' + #root.methodName + '#' + #name).replaceAll('[^0-9a-zA-Z:#._]', '')",
            unless = "#result == null || #result == ''",
            condition = "#name != null",
            sync = false
    )
    @RequestMapping("baseCache")
    public String baseCache(String name){
        logger.info("Into BaseCache Controller, {}", name);
        String result = cacheService.baseCache(name);
        return "OK" + " - " + name + " - " + result;
    }

    @UseCache
    @RequestMapping("interceptorCache")
    public String interceptorCache(String name){
        logger.info("Into BaseCache Controller, {}", name);
        String result = cacheService.incr(name);
        return "OK" + " - " + name + " - " + result;
    }

    @RequestMapping("/aopCacheHasKey")
    public String aopCacheHasKey(String name){
        String result = cacheAopService.hasKeys(name);
        return "ok" + " - " + name + " - " + result;
    }


    @RequestMapping("/aopCacheAllDef")
    public String aopCacheAllDef(String name){
        String result = cacheAopService.allDef(name);
        return "ok" + " - " + name + " - " + result;
    }
}
