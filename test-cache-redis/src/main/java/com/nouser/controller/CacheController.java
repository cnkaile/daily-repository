package com.nouser.controller;

import com.nouser.config.annotations.UseCache;
import com.nouser.service.CacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/cache")
public class CacheController {
    private static final Logger logger = LoggerFactory.getLogger(CacheController.class);

    @Resource
    private CacheService cacheService;

    @RequestMapping("baseCache")
    private String baseCache(String name){
        logger.info("Into BaseCache Controller, {}", name);
        String result = cacheService.baseCache(name);

        return "OK" + " - " + name + " - " + result;
    }

    @UseCache
    @RequestMapping("interceptorCache")
    private String interceptorCache(String name){
        logger.info("Into BaseCache Controller, {}", name);
        String result = cacheService.incr(name);

        return "OK" + " - " + name + " - " + result;
    }

}
