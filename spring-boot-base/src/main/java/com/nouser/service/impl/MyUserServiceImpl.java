package com.nouser.service.impl;

import com.nouser.mapper.MyUserMapper;
import com.nouser.entity.MyUser;
import com.nouser.service.MyUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MyUserServiceImpl implements MyUserService {
    private static final Logger logger = LoggerFactory.getLogger(MyUserServiceImpl.class);

    @Autowired
    private MyUserMapper myUserMapper;

    @Override
    public MyUser getUser(int id) {
        return myUserMapper.getUserById(id);
    }
}
