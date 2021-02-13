package com.nouser.dao;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.nouser.entity.User;
import com.nouser.service.RedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class UserRedisDao {
    private static final Logger logger = LoggerFactory.getLogger(UserRedisDao.class);
    @Autowired
    private RedisService redisService;

    public List<User> getList() {
        String str = redisService.getStr("user:list");
        if (StringUtils.isEmpty(str)) {
            return null;
        } else {
            return JSON.parseArray(str, User.class);
        }
    }

    public String add(User user){
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return redisService.set("user:obj", user);
    }


}
