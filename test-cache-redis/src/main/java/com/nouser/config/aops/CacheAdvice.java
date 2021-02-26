package com.nouser.config.aops;


import com.nouser.config.annotations.UseAopCache;
import com.nouser.utils.CacheOperatorExpression;
import com.nouser.utils.JsonUtils;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class CacheAdvice {
    private static final Logger logger = LoggerFactory.getLogger(CacheAdvice.class);
    private CacheOperatorExpression cacheOperatorExpression = new CacheOperatorExpression();

    private final StringRedisTemplate redisTemplate;

    @Autowired
    public CacheAdvice(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 切入点声明，即切入到哪些目标方法。value 属性指定切入点表达式，默认为 ""。
     * 用于被下面的通知注解引用，这样通知注解只需要关联此切入点声明即可，无需再重复写切入点表达式
     * <p>
     * 切入点表达式常用格式举例如下：
     * - * com.nouser.controller.CacheController.*(..))：表示 com.nouser.controller.CacheController 类中的任意方法
     * - * com.nouser.controller.*.*(..))：表示 com.nouser.controller 包(不含子包)下任意类中的任意方法
     * - * com.nouser.controller..*.*(..))：表示 com.nouser.controller 包及其子包下任意类中的任意方法
     * </p>
     */
    @Pointcut(value = "execution(* com.nouser..*.*(..))")
    public void cachePointcut() {
    }

    @Pointcut(value = "@annotation(com.nouser.config.annotations.UseAopCache) || @within(com.nouser.config.annotations.UseAopCache)")
    public void cacheAnnotationPointcut() {
    }

    @Around(value = "@annotation(useAopCache)")
    public Object aroundAdvice2(ProceedingJoinPoint joinPoint, UseAopCache useAopCache) throws Throwable {
        String keySpel = useAopCache.customKey();
        //获取Redis缓存Key
        String key = getRedisKey(joinPoint, keySpel);
        //读取redis数据
        String result = redisTemplate.opsForValue().get(key);
        if(StringUtils.isNoneBlank(result)){
            logger.info("having cache :{}", result);
            //存在缓存结果
            return JsonUtils.parseObject4G(result);
        }
        //不存在则执行方法.
        Object returnObject = joinPoint.proceed();
        if(returnObject == null){
            return returnObject;
        }
        //不缓存null了就
        String cacheJson = JsonUtils.toGJsonString(returnObject);
        logger.info("Non Cache Set cache:{}", cacheJson);
        //设置缓存,时间是在注解中配置
        redisTemplate.opsForValue().set(key, cacheJson, useAopCache.timeOut(), TimeUnit.MILLISECONDS);
        return returnObject;
    }

    private String getRedisKey(ProceedingJoinPoint joinPoint, String keySpel) {
        if (StringUtils.isNoneBlank(keySpel)) {
            return cacheOperatorExpression.generateKey(keySpel, joinPoint);
        }
        return defaultKey(joinPoint);
    }

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


    //    @Around(value = "@within(useAopCache)")
    public Object aroundAdvice3(ProceedingJoinPoint joinPoint, UseAopCache useAopCache) throws Throwable {
        logger.debug("into around - 3, customKey = {}", useAopCache == null ? "null" : useAopCache.customKey());
        Object returnObject = null;
        returnObject = joinPoint.proceed();
        return returnObject;
    }

    //    @Around(value = "@annotation(useAopCache) || @within(useAopCache)")
    public Object aroundAdvice4(ProceedingJoinPoint joinPoint, UseAopCache useAopCache) throws Throwable {
        logger.debug("into around - 4, nameM = {}, nameC = {}", useAopCache == null ? "null" : useAopCache.customKey());
        Object returnObject = null;
        returnObject = joinPoint.proceed();
        return returnObject;
    }

    /**
     * 前置通知：目标方法执行之前执行以下方法体的内容。
     * value：绑定通知的切入点表达式。可以关联切入点声明，也可以直接设置切入点表达式
     * <br/>
     *
     * @param joinPoint：提供对连接点处可用状态和有关它的静态信息的反射访问<br/> <p>
     *                                                 Object[] getArgs()：返回此连接点处（目标方法）的参数
     *                                                 Signature getSignature()：返回连接点处的签名。
     *                                                 Object getTarget()：返回目标对象
     *                                                 Object getThis()：返回当前正在执行的对象
     *                                                 StaticPart getStaticPart()：返回一个封装此连接点的静态部分的对象。
     *                                                 SourceLocation getSourceLocation()：返回与连接点对应的源位置
     *                                                 String toLongString()：返回连接点的扩展字符串表示形式。
     *                                                 String toShortString()：返回连接点的缩写字符串表示形式。
     *                                                 String getKind()：返回表示连接点类型的字符串
     *                                                 </p>
     */
//    @Before(value = "cachePointcut()")
    public void beforeAdvice(JoinPoint joinPoint) {
        String format = String.format("args:%s, signature:%s, target:%s, this:%s, toLongString:%s, toShortSrt:%s, kind:%s",
                Arrays.toString(joinPoint.getArgs()), joinPoint.getSignature().toString(), joinPoint.getTarget(), joinPoint.getThis(), joinPoint.toLongString(), joinPoint.toShortString(), joinPoint.getKind());
        logger.debug("Hello Into Before Advices, {}", format);

    }

    /**
     * 后置通知：目标方法执行之后执行以下方法体的内容，不管目标方法是否发生异常。
     * value：绑定通知的切入点表达式。可以关联切入点声明，也可以直接设置切入点表达式
     */
//    @After(value = "cachePointcut()")
    public void afterAdvice(JoinPoint joinpoint) {
        logger.debug("Hello Into After Advices");
    }

    /**
     * 返回通知：目标方法返回后执行以下代码
     * value 属性：绑定通知的切入点表达式。可以关联切入点声明，也可以直接设置切入点表达式
     * pointcut 属性：绑定通知的切入点表达式，优先级高于 value，默认为 ""
     * returning 属性：通知签名中要将返回值绑定到的参数的名称，默认为 ""
     *
     * @param joinPoint ：提供对连接点处可用状态和有关它的静态信息的反射访问
     * @param result    ：目标方法返回的值，参数名称与 returning 属性值一致。无返回值时，这里 result 会为 null.
     */
//    @AfterReturning(pointcut = "cachePointcut()", returning = "result")
    public void aspectAfterReturning(JoinPoint joinPoint, Object result) {
        logger.debug("Hello Into AfterReturning {}, result = {}", joinPoint.getSignature().getDeclaringTypeName(), JsonUtils.toGJsonString(result));
    }

    /**
     * 异常通知：目标方法发生异常的时候执行以下代码，此时返回通知不会再触发
     * value 属性：绑定通知的切入点表达式。可以关联切入点声明，也可以直接设置切入点表达式
     * pointcut 属性：绑定通知的切入点表达式，优先级高于 value，默认为 ""
     * throwing 属性：与方法中的异常参数名称一致，
     *
     * @param e：捕获的异常对象，名称与 throwing 属性值一致
     */
//    @AfterThrowing(pointcut = "cachePointcut()", throwing = "e")
    public void aspectAfterThrowing(JoinPoint jp, Exception e) {
        String methodName = jp.getSignature().getName();
        logger.error("method:{}, Exception:{}", methodName, e.getMessage(), e);
    }

}
