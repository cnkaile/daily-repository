package com.nouser.test;

import com.nouser.TestBase;
import com.nouser.service.CacheService;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class TestCache extends TestBase {
    @Autowired
    private CacheService cacheService;

    @Test
    public void test02(){
        List<String> list = cacheService.getList("zhoukl");
        System.out.println(list);

    }

    @Test
    public void test01(){
        String zhoukl = cacheService.incr("zhoukl");
        System.out.println(zhoukl);
    }
}
