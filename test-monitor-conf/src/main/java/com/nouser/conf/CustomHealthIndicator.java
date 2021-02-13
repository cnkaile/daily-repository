package com.nouser.conf;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

/**
 * 除了使用自动引入的健康指示器之外，我们也可以自定义一个 Health Indicator，只需要实现 HealthIndicator 接口或者继承 AbstractHealthIndicator 类。
 */
@Component
public class CustomHealthIndicator extends AbstractHealthIndicator {
    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        //使用 builder 来创建健康状态信息
        //如果你throw 了一个Exception，那么status就会被置为DOWN，异常记录会被记录下来
        builder.up()
                .withDetail("app","报告：app很健康！")
                .withDetail("error","报告：项目有问题");

    }
}
