# 如何使用Redis做缓存

我们都知道Redis作为NoSql数据库的代表之一，通常会用来作为缓存使用。也是我在工作中通常使用的缓存之一。

## 1、我们什么时候缓存需要用到Redis？

我认为，缓存可以分为两大类：本地缓存和分布式缓存。当我们一个分布式系统就会考虑到缓存一致性的问题，所以需要使用到一个快速的、高并发的、灵活的存储服务，那么Redis就能很好的满足这些。

- 本地缓存：

    即把缓存信息存储到应用内存内部，不能跨应用读取。所以这样的缓存的读写效率上是非常高的，因为节省了http的调用时间。问题是不能跨服务读取，在分布式系统中可能会找成每个机器缓存内容不同的问题。

- 分布式缓存：

    即把缓存内容存储到单独的缓存系统中，当调用时，去指定缓存服务取数据，因此就不会出现本地缓存的多系统缓存数据不同的问题。
 
SpringBoot连接Redis配置(本来懒得写的, 但是我还是追求完美一点):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
    <version>2.4.2</version>
</dependency>
```
RedisClient我使用的是SpringBoot自带的lettuce框架，而并非jredis。

```properties
spring.redis.database=0
# Redis服务器地址
spring.redis.host=81.70.xx.xx记得改喽,如果没有,可以私信我,我吧我的告诉你
# Redis服务器连接端口
spring.redis.port=6379
spring.redis.timeout=5000
# Redis服务器连接密码（默认为空）
spring.redis.password=zxxx
spring.redis.lettuce.pool.max-active=8
spring.redis.lettuce.pool.min-idle=1
spring.redis.lettuce.pool.max-idle=8
spring.redis.lettuce.pool.max-wait=500ms
spring.redis.lettuce.shutdown-timeout=100ms
```
    
## 2、 缓存雏形 - 根据业务逻辑手撸代码

在不需要大面积使用缓存的系统中，我们通常把Redis作为一种中间工具去使用。需在代码逻辑中加入自己的判断。

```java
    public String baseCache(String name) {
        if(StringUtils.isBlank(name)){
            logger.error("Into BaseCache Service, Name is null.");
            return null;
        }
        logger.info("Into BaseCache Service, {}", name);
        //手动加入缓存逻辑
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

### 3.1 先定义一个注解

   我们通过这个注解来区别方法是否需要缓存，注解放到方法上，此方法的返回结果将会被缓存。

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

### 3.3 使用Spring Aop + 注解实现缓存

上面我们说到了使用拦截器实现时，只能对整个接口进行缓存。所以我们换一种思路：面向切面编程，即使用AOP。

SpringBoot Aop专用包:

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>
```


#### 3.3.1 我们对上一个的缓存再优化一下吧

通常我们使用缓存会存在各种不同的需求,如缓存key,缓存时间,缓存条件等等。所以我们学着CacheAble注解,使用Spel表达式自定义key和超时时间。

```java
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
```

#### 3.3.2 AOP解决思路

Spring AOP相对与拦截器来说提供了更好的参数支持，所以我们能够更加全面的进行缓存操作。Aop中有前置通知、后置通知、返回通知、异常通知、环绕通知这几种，具体的区别就不在这里仔细讲解了，关注后续我的文档吧，我会写一个专门介绍Aop的文章。
这里我们就选用环绕通知，因为一个环绕通知就完全解决我们的缓存问题。使得缓存面可以缩小到每个方法上。

1. 实现缓存拦截的切入点 -- 注解方法/类
    
    我们可以直接在AOP中配置切入点，我们使用的是通过注解来判断是否缓存及其缓存策略，恰好AOP同样支持。
    如果我们需要对整个类进行拦截缓存，我们的AOP同样可以完美实现。（我的DEMO中就不再细说了，我只说一下方法上注解，关于注解放到类上自己琢磨一下，道理都是一样的）
    
    所以说，我们通过AOP来绑定具体的拦截方法

2. 实现缓存 -- 环绕通知

    AOP面向切面编程是非常灵活的，我就特别喜欢环绕通知。
    选择环绕通知因为：1、一个方法可以满足我们的现在做缓存的需求；2、方法执行前后可控；3、可获取更多的参数，包括但不局限于目标方法、形参、实参、目标类等；4、拥有更全面的参数就可以至此更全面的Spel表达式；5、可直接获取方法返回值；等等
    
    我们可以在执行方法前判断是否存在缓存，不存在缓存我们再继续执行方法，否则直接返回Redis中的缓存数据了。
    
3. 缓存灵活性 -- 注解变量及其Spel表达式

    像CacheAble一样支持Spel表达式其实就是为了满足更多的业务需求。比如自定义缓存key、设置不同的缓存时间、设置缓存条件和不缓存条件、设置更新缓存条件等等。所以这里需要使用注解中的一些东西去动态的判断缓存逻辑。
    我先举个例子：使用spel自定义缓存key。如果有兴趣，可以根据这个继续扩展。

#### 3.3.3 具体实现

逻辑很简单：
1. 环绕通知前, 解析缓存Key, 判断Redis中是否存在缓存
2. 不存在缓存就执行目标方法
3. 获取到方法执行结果, 进行缓存
4. 返回此次结果

```java
@Aspect
@Component
public class CacheAdvice {
    /** 用来解析Spel表达式, 这个是我自己实现的一个类,下面会具体详解 */
    private CacheOperatorExpression cacheOperatorExpression = new CacheOperatorExpression();
    /** Redis */
    private final StringRedisTemplate redisTemplate;
    @Autowired
    public CacheAdvice(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    @Pointcut(value = "execution(* com.nouser..*.*(..))")
    public void cachePointcut() { }
    /** 
    * 我们的切入点就是含有@UseAopCache注解的方法,@annotation里填的是对应的参数的名字,Aop会自动封装。
    * 当然，我们也可以使用@annotation(com.nouser.config.annotations.UseAopCache), 这样的话, 注解需要我们自己从joinPoint中解析。
    * 同样支持使用@Pointcut(value = "@annotation(com.nouser.config.annotations.UseAopCache)定义切面。
    * 我这里也定义了一个切面cachePointcut(), 取了并的关系, 是为了防止注解越界吧, 万一引用的包中存在同名的注解呢. 
    */
    @Around(value = "cachePointcut() && @annotation(useAopCache)")
    public Object aroundAdvice(ProceedingJoinPoint joinPoint, UseAopCache useAopCache) throws Throwable {
        String keySpel = useAopCache.customKey();
        //获取Redis缓存Key
        String key = getRedisKey(joinPoint, keySpel);
        //读取redis数据缓存数据
        String result = redisTemplate.opsForValue().get(key);
        if(StringUtils.isNoneBlank(result)){
            //存在缓存结果, 将缓存的json转换成Object返回
            return JsonUtils.parseObject4G(result);
        }
        //不存在缓存数据,执行方法, 获取结果, 再放入Redis中
        Object returnObject = joinPoint.proceed();
        //这里我没有对null数据进行缓存, 也可以在注解中设置对应的不缓存策略
        if(returnObject == null){
            return returnObject;
        }
        // 转换结果为Json
        String cacheJson = JsonUtils.toGJsonString(returnObject);
        // 将Json缓存到Redis, 不要忘记重注解中获取缓存时间, 设置Redis的key过期时间
        redisTemplate.opsForValue().set(key, cacheJson, useAopCache.timeOut(), TimeUnit.MILLISECONDS);
        return returnObject;
    }
    /**
    * 从joinPoint中获取方法的上下文环境,然后从Spel表达式中解析出key
    */
    private String getRedisKey(ProceedingJoinPoint joinPoint, String keySpel) {
        if (StringUtils.isNoneBlank(keySpel)) {
            return cacheOperatorExpression.generateKey(keySpel, joinPoint);
        }
        return defaultKey(joinPoint);
    }

    /**
    * 如果没有在注解的customKey()中设置Spel表达式, 我们总不能报错吧, 这里提供一个默认的Key, 数据都冲joinPoint中获取
    * packageName + ':' + methodName + '#' + param
    * 为防止param中存在特殊字符, 这里之保留[a-zA-Z0-9:#_.]
    */
    private String defaultKey(ProceedingJoinPoint joinPoint) {
        StringBuilder key = new StringBuilder();
        String className = joinPoint.getTarget().getClass().getName();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String methodName = method.getName();
        Object[] args = joinPoint.getArgs();
        key.append(className).append(":").append(methodName).append("#");
        if (args != null && args.length > 0) {
            for (Object arg : args) {
                key.append(arg).append("#");
            }
        }
        return key.toString().replaceAll("[^a-zA-Z0-9:#_.]", "");
    }
}

``` 

#### 3.3.4 Spel表达式解析(简单介绍一下, 具体请关注以后的博客)

Spel表达式(赶快画重点了, 这是个非常新奇的东西, 会有很多小妙用的), 全称:Spring Expression Language, 类似于Struts2x中使用的OGNL表达式语言，能在运行时构建复杂表达式、存取对象图属性、对象方法调用等等，并且能与Spring功能完美整合，如能用来配置Bean定义。
SpEL是单独模块，只依赖于core模块，不依赖于其他模块，可以单独使用。

我们主要是对注解中的自定义key进行解析, 生成缓存真正key.

解析Spel表达式主要需要两个参数：解析器和上下文环境。
解析器:org.springframework.expression.spel.standard.SpelExpressionParser
上下文环境我看网上大多直接使用的org.springframework.expression.spel.support.StandardEvaluationContext, 但是我们在这个注解中主要是相关方法的解析, 所以建议使用StandardEvaluationContext的子类org.springframework.context.expression.MethodBasedEvaluationContext

在Spring中解析CacheAble注解中的key同样是使用MethodBasedEvaluationContext的子类. MethodBasedEvaluationContext在添加上下文环境的变量时,使用了懒加载, 当我们注解中的key不使用参数时,就不再添加上下文的变量,在使用的时候才去进行懒加载. 
而且相对于网上的一些实现, 官方实现更加靠谱. 也更加全面. 

我对MethodBaseEvaluationContent简单做了一层封装,注释也很详细,有一些需要注意的东西就看看代码吧. 代码如下:

```java
/**
 * 解析Spel表达式
 */
@Component
public class CacheOperatorExpression {
    /** 这个是Spring 提供的一个方法, 为了获取程序在运行中获取方法的实参 */
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    /** 这里对targetMethod做了一个缓存, 防止每次都去解析重新获取targetMethod */
    private final Map<AnnotatedElementKey, Method> targetMethodCache = new ConcurrentHashMap<>(64);
    /** Spel的解析器 */
    private SpelExpressionParser parser;
    /** 构造 */
    public CacheOperatorExpression() {
        this.parser = new SpelExpressionParser();
    }
    /** 构造 */
    public CacheOperatorExpression(SpelExpressionParser parser) {
        this.parser = parser;
    }
    public SpelExpressionParser getParser(SpelExpressionParser parser) {
        return this.parser;
    }
    private ParameterNameDiscoverer getParameterNameDiscoverer() {
        return this.parameterNameDiscoverer;
    }
    /** 这里创建获取对应的上下文环境 */
    public EvaluationContext createEvaluationContext(Method method, Object[] args, Object target, Class<?> targetClass, Method targetMethod) {
        /* 
         * rootObject,MethodBasedEvaluationContext的一个参数,可以为null,但是如果为null, 在StandardEvaluationContext构造中会设置rootObject = new TypedValue(rootObject)也就是rootObject = TypedValue.NULL; 
         * 这时我们在Spel表达式中就不能使用#root.xxxx获取对应的值.
         * 为了能够使用#root我自定义了一个CacheRootObject
         */
        CacheRootObject rootObject = new CacheRootObject(method, args, target, targetClass);
        return new MethodBasedEvaluationContext(rootObject, targetMethod, args, getParameterNameDiscoverer());
    }

    /**
     * 解析 spel 表达式
     * @return 执行spel表达式后的结果
     */
    public <T> T parseSpel(String spel, Method method, Object[] args, Object target, Class<?> targetClass, Method targetMethod, Class<T> conversionClazz) {
        EvaluationContext context = createEvaluationContext(method, args, target, targetClass, targetMethod);
        return this.parser.parseExpression(spel).getValue(context, conversionClazz);
    }

    public String generateKey(String spel, ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        Object target = joinPoint.getTarget();
        Class<?> targetClass = joinPoint.getTarget().getClass();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Method targetMethod = getTargetMethod( method, targetClass);
        return parseSpel(spel, method, args, target, targetClass, targetMethod, String.class);
    }
    /**
    * 获取targetMethod
    * TargetMethod和Method？
    * 我们使用joinPoint获取的Method可能是一个接口方法,也就是我们把Aop的切点放在了接口上或接口的方法上。所以我们需要获取到运行的对应Class上的此方法。
    * eg: 我们获取的{@code PersonBehavior.eatFood()}的Class可能是{@code ChildBehavior}或者{@code DefaultPersonBehavior}的. 他们都会对eatFood()进行覆盖, 
    * 而如果切点放在Class PersonBehavior上, 那么通过joinPoint获取的Method实际并不是程序调用的Method。
    * 所以我们需要通过程序调用的Class去反解析出真正调用的Method就是targetMethod.
    */
    private Method getTargetMethod(Method method, Class<?> targetClass) {
        AnnotatedElementKey methodKey = new AnnotatedElementKey(method, targetClass);
        Method targetMethod = this.targetMethodCache.get(methodKey);
        if (targetMethod == null) {
            targetMethod = AopUtils.getMostSpecificMethod(method, targetClass);
            if (targetMethod == null) {
                targetMethod = method;
            }
            this.targetMethodCache.put(methodKey, targetMethod);
        }
        return targetMethod;
    }

}
// ############################################
/**
* 自定义的RootObject, 让spel表达式至此#root参数, #root就是对应这个Object, #root.method就是对应这个类中的Method
*/
public class CacheRootObject {
    private final Method method;
    private final Object[] args;
    private final Object target;
    private final Class<?> targetClass;
    public CacheRootObject( Method method, Object[] args, Object target, Class<?> targetClass) {
        this.method = method;
        this.target = target;
        this.targetClass = targetClass;
        this.args = args;
    }
    /* get set方法*/
}
```

#### 3.3.5 反馈

毕竟是我们自己实现的代码, 没有千锤百练谁也不能说完美. 请问世间是否存在完美的代码,除了HelloWorld只求产品不改需求.

1. 麻烦!!! 不管多少代码, 不管自己的逻辑有多么完美, 但是还是要自己写啊, 万一改需求了这个缓存逻辑行不通了呢, 程序员事情很多的好吧.

2. 懒, 谁也想不起来那么多的业务逻辑, 老板也不会给你太多时间让你去开发个灵活的“框架？？”

3. 有没有更好的方法呢, 就那种配置配置就能使用的那种, 不用担心出现bug的那种, 即使出现了bug能推出去的那种, 特别特别好使用的那种, 反正就不是我写的代码bug就不是我的那种. 反正老板也是只看结果.

4. 如果你使用的是SpringBoot, 还真有. 

### 4 SpringBoot整合Redis缓存

Redis那么一个经典的NoSql数据库,SpringBoot缓存肯定也对它进行支持. SpringBoot的缓存功能已经为我们提供了使用Redis做缓存.

#### 4.1 引入环境

上面我们已经引入了Redis,这里我们还需要引入SpringBoot的Cache包

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-cache</artifactId>
        </dependency>
```

#### 4.2 查找SpringBoot对Redis缓存的支持

随便百度一下或者google一下都能找到SpringBoot对各种缓存的支持都是实现接口:org.springframework.cache.annotation.CachingConfigurer

注释上同样写的大概意思就是：我们使用org.springframework.context.annotation.Configuration配置的实现类, 能够为注解@EnableCaching实现缓存解析和缓存管理器
所以, 我们只需要实现此接口, 就可以直接使用@EnableCaching注解进行缓存管理. 

我们可在Ide上查看CachingConfigurer接口的子类可以看到好像并没有关于Redis的实现。所以我们就需要手动去实现这个接口了。较好的是CachingConfigurere接口中注释的非常清楚。大家可以看一下源码

```java
/**
 * Interface to be implemented by @{@link org.springframework.context.annotation.Configuration
 * Configuration} classes annotated with @{@link EnableCaching} that wish or need to
 * specify explicitly how caches are resolved and how keys are generated for annotation-driven
 * cache management. Consider extending {@link CachingConfigurerSupport}, which provides a
 * stub implementation of all interface methods.
 *
 * <p>See @{@link EnableCaching} for general examples and context; see
 * {@link #cacheManager()}, {@link #cacheResolver()} and {@link #keyGenerator()}
 * for detailed instructions.
 */
public interface CachingConfigurer {
    /** 缓存管理器 */
	@Nullable
	CacheManager cacheManager();
	/** 缓存解析器,注解上说是一个比缓存管理器更加强大的实现. 他和cacheManager互斥, 只能存在一个, 两个都有的话会报异常.
	 * 这次我使用的是CacheManager, 因为之前我尝试CacheResolver的时候使用SimpleCacheResolver然后在CacheManager中自定义的缓存过期时间不生效.然后没有研究了, 下次研究完我再补上 */
	@Nullable
	CacheResolver cacheResolver();
	/** key序列化方式 */
	@Nullable
	KeyGenerator keyGenerator();
	/** 错误处理 */
	@Nullable
	CacheErrorHandler errorHandler();

}

```

#### 4.3 缓存管理器CacheManager

虽然SpringBoot没有给我们实现CachingConfigurer, 但是缓存管理器是已经帮助我们实现了的。我们引入了cache包后，会存在一个RedisCacheManager, 我们的缓存管理器就使用它来实现.

```java
RedisCacheManager redisCacheManager = RedisCacheManager.builder(connectionFactory).build();
```

我们使用RedisCacheManager提供的builder静态方法去创建, 需要参数链接工厂, 即需要一个能够创建Redis链接的对象, 这个对象存在于Spring容器中, 我们直接通过注解获取即可. 

我们使用redisCacheConfiguration来做一些配置, 比如key的前缀、key/value的序列化方式、缓存名称和对应的缓存时间等等。

redis KeyValue的序列化方式：key就选用的StringRedisSerializer,而value我们大多都会选择使用json. 这些序列化方式都是实现的RedisSerializer<Object>接口, 官方实现有String序列化、jdk序列化、oxm序列化、json序列化
其中json序列化有FastJson和Jackson, 我这里选用的是Jackson, 可以根据业务去选择或者自己去实现RedisSerializer<Object>接口。

```java
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
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                // 设置value的序列化方式
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
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
```

#### 4.4 异常处理

我们在看CachingConfigurer时, 会发现我们会获取一个CacheErrorHandler的类, 这个类就是对缓存过程中出现异常时对异常进行操作的对象.

CacheErrorHandler是一个接口,这个接口提供了对: 获取缓存异常、设置缓存异常、解析缓存异常、清除缓存异常 这五种异常的处理. 
官方给出了一个默认实现SimpleCacheErrorHandler,默认实现就像名称一样很简单, 把异常抛出, 不做任何处理, 但是如果抛出异常,就会对我们的业务逻辑存在影响。
eg:我们的缓存Redis突然宕机, 如果仅仅因为缓存宕机就导致服务异常不可用那就太尴尬了,所以不建议使用默认的SimpleCacheErrorHandler, 所以我建议自己去实现这个, 我这里选择了打日志的方式处理. 即使缓存不可用,仍然可以走正常的逻辑去获取. 可能这会对下游服务造成压力,这就看你的实现了.

```java

/**
* 异常处理接口
*/
public interface CacheErrorHandler {

	void handleCacheGetError(RuntimeException exception, Cache cache, Object key);

	void handleCachePutError(RuntimeException exception, Cache cache, Object key, @Nullable Object value);

	void handleCacheEvictError(RuntimeException exception, Cache cache, Object key);

	void handleCacheClearError(RuntimeException exception, Cache cache);
}
/** 官方默认实现 */
public class SimpleCacheErrorHandler implements CacheErrorHandler {
	@Override
	public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
		throw exception;
	}
	@Override
	public void handleCachePutError(RuntimeException exception, Cache cache, Object key, @Nullable Object value) {
		throw exception;
	}
	@Override
	public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
		throw exception;
	}
	@Override
	public void handleCacheClearError(RuntimeException exception, Cache cache) {
		throw exception;
	}
}

/**
* 我的实现, 效果可能和官方实现相反, 但是都没有对异常进行处理.
*/
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

```

#### 4.5 完整代码

上面说了那么多只是为了让大家好理解而已, 在SpringBoot项目中只需要创建一个下面的类即可.

这个依赖Redis的配置, 如何配置Redis在上面

```java
@Configuration
@EnableCaching
public class RedisCacheConfig extends CachingConfigurerSupport {
    private static final Logger logger = LoggerFactory.getLogger(RedisCacheConfig.class);
    /**
    * redis
    */
    @Autowired
    private RedisConnectionFactory connectionFactory;
    /** 我自定义了一个前缀, 去区分环境 */
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
     * 默认的key生成策略, 包名 + 方法名。建议使用Cacheable注解时使用Spel自定义缓存key. 
     */
    @Bean
    @Override
    public KeyGenerator keyGenerator() {
        return (o, method, params) -> o.getClass().getName() + ":" + method.getName();
    }

    /**
     * 设置读写缓存异常处理
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
        CacheTimes[] times = CacheTimes.values();//我把过期时间分阶段做了一个enum类, 然后遍历, 后续使用时也使用这个enum去设置时间
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


    /**
    * 自定义异常处理
    */
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
```

#### 4.6 使用: @Cacheable

配置好了, 我们如何使用呢？

我们现在是使用的SpringBoot缓存整合Redis, 所以我们只需要使用注解@Cacheable, 我们先看一下Cacheable注解, 然后说一下它如何使用.

```java
/** 这里只贴代码, 注释自己去ide看吧, 源码上的注释挺全的 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface Cacheable {
    /** 缓存名,和前面我们设置缓存管理器时初始化缓存名称和配置一一对应, 如果为空, 则取默认配置 */
	@AliasFor("cacheNames")
	String[] value() default {};
	@AliasFor("value")
	String[] cacheNames() default {};
    /** 设置缓存的key, 每个缓存key是唯一的, 我们使用Redis缓存, 那么它生成的结果就是我们的Redis Key */
	String key() default "";
    /** 指定key生成策略*/
	String keyGenerator() default "";
    /** 指定缓存管理器 */
	String cacheManager() default "";
    /** 制定解析器 */
	String cacheResolver() default "";
    /** 是否走缓存逻辑, 缓存前进行判定, 是否走缓存逻辑, 支持Spel表达式, 如果返回false, 将会跳过缓存逻辑 */
	String condition() default "";
    /** 是否进行缓存, 这个是在执行目标方法后进行判断, 支持Spel表达式, 如果为true, 将不会对结果进行缓存 */
	String unless() default "";
	/** 是否使用同步 */
	boolean sync() default false;
}

```

    单独说一下sync(), 如果我们设置sync为true, 那么我们执行到获取缓存的get方法时, 这个方法是访问的加锁的同步方法,只能同步调用,但是保证了缓存失效时不会全部请求都到下游服务请求。
    注解也非常清楚:参考org.springframework.data.redis.cache.RedisCache.get, 可以自己打断点试一试, 反正这个不建议使用, 除非业务不影响业务的且需要保证下游服务的前提下.

关于Cacheable注解的使用.....我举几个例子吧

```java
/**
* 缓存key = packageName + ":" + methodName + "#" + #name + "#" + #id
* 如果方法结果为null 或长度 小于1 则不缓存此结果
* 参数useCache = true 的时候才走缓存逻辑, 
*/
@Cacheable(value = "xxxx",
    key = "(#root.targetClass.getName() + ':' + #root.methodName + '#' + #name + '#' + #id).replaceAll('[^0-9a-zA-Z:#._]', '')",
    unless = "#result == null || #result.size() < 1",
    condition = "#useCache"
)
public List<String> cache01(String name, String id, boolean useCache){}
```

