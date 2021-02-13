package com.nouser.conf;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@Configuration
public class RedisConf {

    @Autowired
    private RedisProperties redisProperties;

    @Bean
    public JedisPool jedisPool() {
        RedisProperties.Pool pool1 = redisProperties.getJedis().getPool();
        RedisProperties.Pool pool = redisProperties.getLettuce().getPool();

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxIdle(pool.getMaxIdle());
        poolConfig.setMaxTotal(pool.getMaxActive());
        poolConfig.setMaxWaitMillis(pool.getMaxWait().toMillis());

        return new JedisPool(poolConfig, redisProperties.getHost(), redisProperties.getPort(),
                Integer.valueOf(String.valueOf(redisProperties.getTimeout().toMillis())),
                redisProperties.getPassword(), 0);


    }
}
