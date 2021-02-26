package com.nouser.config.interceptors;

import com.nouser.config.annotations.UseCache;
import com.nouser.utils.CacheUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

/**
 * 缓存拦截器
 */
@Component
public class CustomCacheInterceptor extends HandlerInterceptorAdapter {
    private static final Logger logger = LoggerFactory.getLogger(CustomCacheInterceptor.class);
    private final StringRedisTemplate stringRedisTemplate;

    @Autowired
    public CustomCacheInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        logger.info("Into Method Bef, This handler :{}", handler.getClass().getName());

        if (handler instanceof HandlerMethod) {
            HandlerMethod method = (HandlerMethod) handler;
            boolean useCache = method.hasMethodAnnotation(UseCache.class);
            boolean methodResponseBody = method.hasMethodAnnotation(ResponseBody.class);
            boolean classResponseBody = method.getBeanType().isAnnotationPresent(ResponseBody.class);
            boolean restController = method.getBeanType().isAnnotationPresent(RestController.class);

            if (useCache && (methodResponseBody || classResponseBody || restController)) {
                logger.info("This Method:{} Is UseCache", method.getMethod().getName());
                String cacheKey = CacheUtils.keySerialization(request, method.getMethod());
                String responseValue = stringRedisTemplate.opsForValue().get(cacheKey);
                if (StringUtils.isNoneBlank(responseValue)) {
                    PrintWriter writer = response.getWriter();
                    writer.append(responseValue);
                    writer.flush();
                    writer.close();
                    response.flushBuffer();
                    return false;
                }
            }
        }

        return true;
    }
}
