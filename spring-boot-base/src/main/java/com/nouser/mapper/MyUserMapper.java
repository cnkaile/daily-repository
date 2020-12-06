package com.nouser.mapper;

import com.nouser.entity.MyUser;
import org.springframework.stereotype.Repository;


@Repository
public interface MyUserMapper {

    public MyUser getUserById(int id);
}
