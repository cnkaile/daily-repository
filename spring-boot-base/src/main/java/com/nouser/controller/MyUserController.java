package com.nouser.controller;

import com.alibaba.fastjson.JSON;
import com.nouser.entity.MyUser;
import com.nouser.service.MyUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/test")
public class MyUserController {

    private static final Logger logger = LoggerFactory.getLogger(MyUserController.class);

    @Autowired
    private MyUserService myUserService;

    @RequestMapping("/get/{id}")
    public String getUser(@PathVariable int id){
        MyUser user = myUserService.getUser(id);
        return JSON.toJSONString(user);
    }

}
