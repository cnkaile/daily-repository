package com.nouser.utils;

import com.nouser.config.CacheRootObject;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.cache.Cache;
import org.springframework.context.expression.AnnotatedElementKey;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.context.expression.CachedExpressionEvaluator;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 解析Spel表达式
 */
@Component
public class CacheOperatorExpression {

    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final Map<AnnotatedElementKey, Method> targetMethodCache = new ConcurrentHashMap<>(64);


    /*
     * 用于SpEL表达式解析.
     */
    private SpelExpressionParser parser;

    public SpelExpressionParser getParser(SpelExpressionParser parser) {
        return this.parser;
    }

    public CacheOperatorExpression() {
        this.parser = new SpelExpressionParser();
    }

    public CacheOperatorExpression(SpelExpressionParser parser) {
        this.parser = parser;
    }

    private ParameterNameDiscoverer getParameterNameDiscoverer() {
        return this.parameterNameDiscoverer;
    }

    public EvaluationContext createEvaluationContext(Method method, Object[] args, Object target, Class<?> targetClass, Method targetMethod) {
        CacheRootObject rootObject = new CacheRootObject(method, args, target, targetClass);
        return new MethodBasedEvaluationContext(rootObject, targetMethod, args, getParameterNameDiscoverer());
    }

    /**
     * 解析 spel 表达式
     *
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
