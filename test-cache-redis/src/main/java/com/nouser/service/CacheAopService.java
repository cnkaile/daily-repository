package com.nouser.service;

import com.nouser.config.annotations.UseAopCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CacheAopService {
    public static final Logger logger = LoggerFactory.getLogger(CacheAopService.class);


    @UseAopCache(customKey =  "#root.targetClass.getName() + ':' + #root.methodName + '#' + #name", timeOut = 10 * 1000)
    public String hasKeys(String name){
        logger.debug("Annotation Method:{}, param:{}", getClass().getName(), name);
        return this.getClass().getName() + name;
    }

    @UseAopCache
    public String allDef(String name) {
        logger.debug("Annotation Method:{}, param:{}", getClass().getName(), name);
        return this.getClass().getName() + name;
    }
}
