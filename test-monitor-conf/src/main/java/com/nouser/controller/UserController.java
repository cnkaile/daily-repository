package com.nouser.controller;

import com.nouser.dao.UserRedisDao;
import com.nouser.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/user")
public class UserController {
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserRedisDao userRedisDao;

    @RequestMapping("/list")
    public List<User> list(){
        return userRedisDao.getList();
    }

    @RequestMapping("/set")
    public String set(String name, String nickname){
        User user = new User();
        user.setIcon("http://icon");
        user.setName(name);
        user.setNickname(nickname);

        return userRedisDao.add(user);

    }

}
