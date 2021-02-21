## 如何使用Redis做缓存

我们都知道Redis作为NoSql数据库的代表之一，通常会用来作为缓存使用。也是我在工作中通常使用的缓存之一。

### 1、我们什么时候缓存需要用到Redis？

我认为，缓存可以分为两大类：本地缓存和分布式缓存。当我们一个分布式系统就会考虑到缓存一致性的问题，所以需要使用到一个快速的、高并发的、灵活的存储服务，那么Redis就能很好的满足这些。

- 本地缓存：

    即把缓存信息存储到应用内存内部，不能跨应用读取。所以这样的缓存的读写效率上是非常高的，因为节省了http的调用时间。问题是不能跨服务读取，在分布式系统中可能会找成每个机器缓存内容不同的问题。

- 分布式缓存：

    即把缓存内容存储到单独的缓存系统中，当调用时，去指定缓存服务取数据，因此就不会出现本地缓存的多系统缓存数据不同的问题。
    
### 2、 缓存雏形 - 根据业务逻辑手撸代码

在不需要大面积使用缓存的系统中，我们通常把Redis作为一种中间工具去使用。需在代码逻辑中加入自己的判断。

```java
    public String baseCache(String name) {
        if(StringUtils.isBlank(name)){
            logger.error("Into BaseCache Service, Name is null.");
            return null;
        }
        logger.info("Into BaseCache Service, {}", name);
        String value = stringRedisTemplate.opsForValue().get("cache_sign:" + name);
        if(!StringUtils.isBlank(value)){
            return value;
        }
        else{
            value = String.valueOf(++BASE_CACHE_SIGN);
            stringRedisTemplate.opsForValue().set("cache_sign:" + name, value, 60, TimeUnit.SECONDS);
            return String.valueOf(BASE_CACHE_SIGN);
        }
    }
```

### 3、通用缓存 - 使用Aop或者Interceptor实现

个别接口或方法我们可以手撸代码，但是不管是后期维护还是代码的通用性都是比较局限的。所以与其在业务逻辑中增加判断逻辑，不如写一个通用的。

#### 3.1 先定义一个注解吧,我们通过这个注解来区别方法是否需要缓存

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface UseCache {
}
```

#### 3.2 使用SpringMvc的拦截器，对接口结果进行缓存。
我们将从Redis取缓存结果提取到拦截器中，


