package com.nouser.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HelloWorld
 */
@RestController
@RequestMapping("/init")
public class HelloWorld {
    private static final Logger logger = LoggerFactory.getLogger(HelloWorld.class);

    @GetMapping("/helloWorld")
    public String helloWorld(String name){
        logger.debug("{}, this is debug log", name);
        logger.info("{}, this is info log", name);
        logger.warn("{}, this is warn log", name);
        logger.error("{}, this is error log", name);

        return name + ", Hello MyWorld.\n";

    }
}
