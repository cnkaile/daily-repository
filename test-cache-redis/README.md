# 如何使用Redis做缓存

我们都知道Redis作为NoSql数据库的代表之一，通常会用来作为缓存使用。也是我在工作中通常使用的缓存之一。

## 1、我们什么时候缓存需要用到Redis？

我认为，缓存可以分为两大类：本地缓存和分布式缓存。当我们一个分布式系统就会考虑到缓存一致性的问题，所以需要使用到一个快速的、高并发的、灵活的存储服务，那么Redis就能很好的满足这些。

- 本地缓存：

    即把缓存信息存储到应用内存内部，不能跨应用读取。所以这样的缓存的读写效率上是非常高的，因为节省了http的调用时间。问题是不能跨服务读取，在分布式系统中可能会找成每个机器缓存内容不同的问题。

- 分布式缓存：

    即把缓存内容存储到单独的缓存系统中，当调用时，去指定缓存服务取数据，因此就不会出现本地缓存的多系统缓存数据不同的问题。
    
## 2、 缓存雏形 - 根据业务逻辑手撸代码

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

## 3、通用缓存 - 使用Aop或者Interceptor实现

个别接口或方法我们可以手撸代码，但是不管是后期维护还是代码的通用性都是比较局限的。所以与其在业务逻辑中增加判断逻辑，不如写一个通用的。

### 3.1 先定义一个注解吧,我们通过这个注解来区别方法是否需要缓存

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface UseCache {
}
```

### 3.2 使用SpringMvc的拦截器，对接口结果进行缓存。
我们将从Redis取缓存结果提取到拦截器中，这样我们就可以只通过一个注解去标识是否执行缓存操作。

#### 3.2.1 拦截器: HandleInterceptorAdapter

拦截器的作用我在这里就不过多的说明。如果在拦截器中发现此接口包含UseCache注解，我们需要检查Redis是否存在缓存，如果存在缓存，则直接返回其值即可。

    RedisClient我使用的是SpringBoot自带的lettuce框架，而并非jredis。

代码如下：

```java
/**
 * 缓存拦截器
 */
@Component
public class CustomCacheInterceptor extends HandlerInterceptorAdapter {
    private static final Logger logger = LoggerFactory.getLogger(CustomCacheInterceptor.class);
    /** RedisClient */
    private final StringRedisTemplate stringRedisTemplate;
    @Autowired
    public CustomCacheInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    /**
    * 我们只需要实现preHandle方法即可，此方法会在接口调用前被调用，所以可以在这里判断缓存，如果存在缓存，直接返回即可。
    */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        logger.info("Into Controller Before. This handler :{}", handler.getClass().getName());
        if (handler instanceof HandlerMethod) {
            HandlerMethod method = (HandlerMethod) handler;
            //判断是否存在我们定义的缓存注解
            boolean useCache = method.hasMethodAnnotation(UseCache.class);
            //我们只对json进行缓存，其他的同理，所以再判断一下这个Controller是哪种接口方式。(包名+方法名+参数)
            boolean methodResponseBody = method.hasMethodAnnotation(ResponseBody.class);
            boolean classResponseBody = method.getBeanType().isAnnotationPresent(ResponseBody.class);
            boolean restController = method.getBeanType().isAnnotationPresent(RestController.class);
            
            if (useCache && (methodResponseBody || classResponseBody || restController)) {
                logger.info("This Method:{} Is UseCache", method.getMethod().getName());
                //我们使用一个工具类去生成这个方法的一个唯一key，使用此key当作redisKey。
                String cacheKey = CacheUtils.keySerialization(request, method.getMethod());
                //从Redis中取数据
                String responseValue = stringRedisTemplate.opsForValue().get(cacheKey);
                if (StringUtils.isNoneBlank(responseValue)) {
                    //此方法存在缓存，且拿到了缓存值，所以直接返回给客户端即可，不需要再继续下一步
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
``` 

#### 3.2.2 ResponseBodyAdvice 和 @ControllerAdvice

上述中我们在拦截器中拦截了使用缓存且存在缓存的请求，直接返回缓存内容。但是还存在一个问题： 我们从哪个地方将数据写入Redis？

我之前考虑再重写HandleInterceptorAdapter.postHandle(...)方法，然后在处理完成Controller后，拦截处理结果，将结果放入Redis。但是出现以下问题：

    1. 虽然能够正常调用postHandle(...)方法，但是大多进行缓存的都是ResponseBody数据，这样的数据并不会存放到ModleAndView中，当然也不会在DispatcherServlet中处理ModleAndView。所以并不能从ModleAndView中获取执行结果。
    2. 我打算从response中找到要返回到客户端的数据。但是从上述方法我们就可以知道，response发送数据是使用流的方式，当Controller执行结束之后，postHandle之前就把数据写入了流中。如果重置输出流太过麻烦。

所以我不能继续使用此拦截器去获取结果。

解决：在调用完Controller之后，response写出之前，Springboot会调用一个接口：org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice。所以我们就可以实现这个接口去对response的body数据进行处理。

代码如下：
````java
/**
* SpringBoot提供RestControllerAdvice注解，此注解为使用@ResponseBody的Controller生成一个Aop通知。
* 然后我们实现了ResponseBodyAdvice的方法：supports(...) 和 beforeBodyWrite(...)
*/
@RestControllerAdvice
public class ControllerResponseBody implements ResponseBodyAdvice<Object> {
    private static final Logger logger = LoggerFactory.getLogger(ControllerResponseBody.class);
    /** RedisClient */
    private final StringRedisTemplate redisTemplate;
    @Autowired
    public ApiResponseBody(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
    * 此方法返回boolean类型值，主要是通过返回值确认是否走beforeBodyWrite方法
    */
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        logger.info("Into supports");
        Method method = returnType.getMethod();
        if(method != null){
            logger.info("Find This method use cache.");
            return method.isAnnotationPresent(UseCache.class);
        }
        return false;
    }

    /**
    * 这个方法调用在response响应之前，且方法参数是包含Controller的处理结果的。
    */
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        try{
            //将返回值转换为json，方便存储到redis。
            String value = JsonUtils.toGJsonString(body);
            // 拼接key
            String cacheKey = CacheUtils.keySerialization(request, returnType.getMethod());
            if(StringUtils.isNoneBlank(cacheKey)){
                // 设置缓存60s
                redisTemplate.opsForValue().set(cacheKey, value, 60, TimeUnit.SECONDS);
            }
            logger.info("cache controller return content.");
        }catch (Exception e){
            logger.error("Cache Exception:{}", e.getMessage(), e);
        }
        return body;
    }
}
````

#### 3.2.3 使用测试

1. 我们设置一下redis,使用SpringBoot默认的Lettuce，因为设置比较简便，而且呢，据说性能也不错，毕竟能让Springboot默认至此，不会差到哪里去

````properties
# Redis数据库索引（默认为0）
spring.redis.database=0
# Redis服务器地址
spring.redis.host=172.0.0.1
# Redis服务器连接端口
spring.redis.port=6379
spring.redis.timeout=500
# Redis服务器连接密码（默认为空）
spring.redis.password=xxx
spring.redis.lettuce.pool.max-active=8
spring.redis.lettuce.pool.min-idle=1
spring.redis.lettuce.pool.max-idle=8
spring.redis.lettuce.pool.max-wait=500ms
spring.redis.lettuce.shutdown-timeout=100ms
````

2. 上面我们家了个拦截器，在Spring中我们通过配置web.xml去注册拦截器，在SpringBoot中更加简单

````java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Autowired
    private CustomCacheInterceptor cacheInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(cacheInterceptor).addPathPatterns("/cache/**");
    }
}
````

3. 对缓存的操作我们分别在通知和拦截其中都已经实现，那么我们就可以使用了，在我们的接口方法中使用@UseCache注解。

````java
@RestController
@RequestMapping("/cache")
public class CacheController {
    private static final Logger logger = LoggerFactory.getLogger(CacheController.class);
    @Resource
    private CacheService cacheService;

    /**
    * 使用@UseCache注解 
    */
    @UseCache
    @RequestMapping("interceptorCache")
    private String interceptorCache(String name){
        logger.info("Into BaseCache Controller, {}", name);
        String result = cacheService.incr(name);
        return "OK" + " - " + name + " - " + result;
    }
}
````

    我就不再复制结果了，自己试一试吧

#### 3.2.4 反馈

这个是一个最基础的缓存了，可以通过自己需求去扩展：如使用spel为注解UseCache自定义缓存key、自定义缓存时间等等。

- 我们使用拦截器的方法有一个局限，即只能对请求的整个接口去做缓存，但是有些时候我们的需求不是对整个接口进行缓存，可能只想对service缓存，可能想对某个sql缓存。所以局限性还是存在的。

### 3.3 使用SpringAop + 注解实现缓存

上面我们说到了使用拦截器实现时，只能对整个接口进行缓存。所以我们换一种思路：面向切面编程，即使用AOP。

