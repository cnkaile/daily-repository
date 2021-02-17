package com.nouser.utils;

import com.alibaba.fastjson.JSON;
import com.google.gson.Gson;
import com.nouser.entity.Home;
import com.nouser.entity.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JsonUtils {
    private static final Logger logger = LoggerFactory.getLogger(JsonUtils.class);

    private static Gson gson = null;
    static {
        gson = new Gson();
    }

    @NonNull
    public static String toGJsonString(Object o){
        if(o == null){
            return "";
        }
        try{
            return gson.toJson(o);
        }catch (Exception e){
            logger.error("Object To JsonString Exception:{}, Object:{}", e.getMessage(), String.valueOf(o), e);
            return "";
        }
    }

    public static void main(String[] args) {
        Person son1 = new Person("111", 1, 25, "北京市", "412723199512115684");
        Person son2 = new Person("222", 0, 18, "周口市", "412723199512115684");
        Person father = new Person("333", 1, 55, "周口市", "412723199512115684");
        Person mather = new Person("444", 0, 54, "周口市", "412723199512115684");

        Home home = new Home();
        home.setFather(father);
        home.setMother(mather);
        List<Person> sons = new ArrayList<>();
        sons.add(son1);
        sons.add(son2);
        home.setSons(sons);
        home.setAddress("");


        String s = JsonUtils.toGJsonString(home);
        System.out.println(s);
        Home h = JSON.parseObject(s, Home.class);
        System.out.println(h.getFather().toString());
        System.out.println(h.getMother().toString());
        System.out.println(Arrays.toString(h.getSons().toArray()));
        System.out.println(h.getAddress());
    }



    private JsonUtils(){}
}
