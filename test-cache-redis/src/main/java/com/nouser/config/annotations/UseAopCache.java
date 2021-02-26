package com.nouser.config.annotations;

import java.lang.annotation.*;

/**
 * 自定义注解使用缓存
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Documented
public @interface UseAopCache {

    /**
     * 自定义缓存Key,支持spel表达式
     */
    String customKey() default "" ;

    /**
     * 超时时间
     */
    long timeOut() default 60000;
}
