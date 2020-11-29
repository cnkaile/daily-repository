package com.nouser.com.nouser.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/init")
public class HelloWorld {

    @GetMapping("/helloWorld")
    public String helloWorld(String name){
        return name + ", Hello MyWorld.\n";
    }
}
