package com.nouser.config;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.support.spring.FastJsonRedisSerializer;
import com.nouser.enums.CacheTimes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleCacheResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis缓存配置
 * 缓存使用要节制，一个接口尽量只使用一个缓存，
 * 因为一旦缓存雪崩，每个缓存都会经过调用Redis超时时间，服务调用就会产生堆积。
 */
@Configuration
@EnableCaching
public class RedisCacheConfig extends CachingConfigurerSupport {
    private static final Logger logger = LoggerFactory.getLogger(RedisCacheConfig.class);

    @Autowired
    private RedisConnectionFactory connectionFactory;
    //SpEL表达式
    @Value("${com.nouser.profiles.active}")
    private String profilesActive;

    /**
     * 有点问题#######################自定义过期时间不生效
     @Bean // important!
     @Override
    public CacheResolver cacheResolver() {
        // configure and return CacheResolver instance
        return new SimpleCacheResolver(cacheManager(connectionFactory));
    }
     */

    /**
     * 设置com.example.demo.cache.RedisConfig#cacheResolver()就不在是用这个了
     */
    @Bean // important!
    @Override
    public CacheManager cacheManager() {
        // configure and return CacheManager instance
        return cacheManager(connectionFactory);
    }

    /**
     * key的默认生成策略，我写的是[前缀] + 包名 + 类名 + 方法名
     * 因为考虑到参数的多种多样，所以默认不以参数参与key的生成条件。
     * 如果需要使用参数作为缓存Redis Key，请使用 @Cacheable注解的key字段
     * @see Cacheable#key()
     * 这个支持SpEL表达式，优先级高于我自定义的这个key生成策略。
     */
    @Bean
    @Override
    public KeyGenerator keyGenerator() {
        return (o, method, params) -> o.getClass().getName() + "#" + method.getName();
    }

    /**
     * 设置读写缓存异常处理，默认使用
     *
     * @see CachingConfigurerSupport#errorHandler()
     * @see CachingConfigurer#errorHandler()
     * 这种方式抛出了异常，为了不影响程序，我们不把异常抛出，收集日志即可。
     */
    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        logger.error("handler redis cache Exception.");
        return new CustomLogErrorHandler();
    }
    /**
     * 自定义Redis缓存管理器
     * 可以参考{@link RedisCacheConfiguration}
     * 设置过期时间可参考：{@link RedisCacheConfiguration#entryTtl(java.time.Duration)}的return值
     */
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultCacheConf = RedisCacheConfiguration.defaultCacheConfig()
                //设置缓存key的前缀生成方式
                .computePrefixWith(cacheName -> profilesActive + "-" +  cacheName + ":" )
                // 设置key的序列化方式
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(keySerializer()))
                // 设置value的序列化方式
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer()))
                // 不缓存null值，但是如果存在空值，org.springframework.cache.interceptor.CacheErrorHandler.handleCachePutError会异常:
                // 异常内容: Cache 'cacheNullTest' does not allow 'null' values. Avoid storing null via '@Cacheable(unless="#result == null")' or configure RedisCache to allow 'null' via RedisCacheConfiguration.
//                .disableCachingNullValues()
//                 默认60s缓存
                .entryTtl(Duration.ofSeconds(60));

        //设置缓存时间,使用@Cacheable(value = "xxx")注解的value值
        CacheTimes[] times = CacheTimes.values();
        Set<String> cacheNames = new HashSet<>();
        //设置缓存时间,使用@Cacheable(value = "user")注解的value值作为key, value是缓存配置，修改默认缓存时间
        ConcurrentHashMap<String, RedisCacheConfiguration> configMap = new ConcurrentHashMap<>();
        for (CacheTimes time : times) {
            cacheNames.add(time.getCacheName());
            configMap.put(time.getCacheName(), defaultCacheConf.entryTtl(time.getCacheTime()));
        }

        //需要先初始化缓存名称，再初始化其它的配置。
        RedisCacheManager redisCacheManager = RedisCacheManager.builder(connectionFactory)
                //设置缓存name
                .initialCacheNames(cacheNames)
                //设置缓存配置
                .withInitialCacheConfigurations(configMap)
                //设置默认配置
                .cacheDefaults(defaultCacheConf)
                //说是与事务同步，但是具体还不是很清晰
                .transactionAware()
                .build();

        return redisCacheManager;
    }
    /**
     * 因为默认key都是字符串，就使用默认的字符串序列化方式，没毛病
     */
    private RedisSerializer<String> keySerializer() {
        return new StringRedisSerializer();
    }

    /**
     * value值序列化方式
     * 使用Jackson2Json的方式存入redis
     * ** 注意,要缓存的类型，必须有 "默认构造(无参构造)" ，否则从json2class时会报异常，提升没有默认构造。
     */
    private GenericJackson2JsonRedisSerializer valueSerializer() {
        GenericJackson2JsonRedisSerializer redisSerializer = new GenericJackson2JsonRedisSerializer();
        return redisSerializer;
    }

    /**
     * 其他集合等转换正常，但是不知道为啥啊RespResult转换异常
     * java.lang.ClassCastException: com.alibaba.fastjson.JSONObject cannot be cast to com.example.demo.util.RespResult
     */
    private FastJsonRedisSerializer valueSerializerFastJson(){
        FastJsonRedisSerializer fastJsonRedisSerializer = new FastJsonRedisSerializer(Object.class);
        return fastJsonRedisSerializer;
    }



    protected class CustomLogErrorHandler implements CacheErrorHandler{
        @Override
        public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
            String format = String.format("RedisCache Get Exception:%s, cache customKey:%s, key:%s", exception.getMessage(), cache.getName(), key.toString());
            logger.error(format, exception);
        }
        @Override
        public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
            String format = String.format("RedisCache Put Exception:%s, cache customKey:%s, key:%s, value:%s", exception.getMessage(), cache.getName(), key.toString(), JSON.toJSONString(value));
            logger.error(format, exception);
        }
        @Override
        public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
            String format = String.format("RedisCache Evict Exception:%s, cache customKey:%s, key:%s", exception.getMessage(), cache.getName(), key.toString());
            logger.error(format, exception);
        }
        @Override
        public void handleCacheClearError(RuntimeException exception, Cache cache) {
            String format = String.format("RedisCache Clear Exception:%s, cache customKey:%s", exception.getMessage(), cache.getName());
            logger.error(format, exception);
        }
    }

}
