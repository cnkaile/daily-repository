package com.nouser.controller;


import org.apache.tomcat.util.http.MimeHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;

@RestController
@RequestMapping("/update")
public class UpdateHeaderController {
    private static final Logger logger = LoggerFactory.getLogger(UpdateHeaderController.class);

    private static final String HEADER_KEY = "test_cookie_id";

    @RequestMapping("/header")
    public String updateHeader(HttpServletRequest servletRequest, String header){
        String headerStr = servletRequest.getHeader(HEADER_KEY);
        logger.info("update before Header value is: {}", headerStr);
        modifyHeader(servletRequest, HEADER_KEY, header);
        return servletRequest.getHeader(HEADER_KEY);
    }


    /**
     * 修改request请求头信息，只适用于Tomcat
     * @param servletRequest    request
     * @param headerKey 修改HeaderKey
     * @param headerValue   修改HeaderValue
     */
    private int modifyHeader(HttpServletRequest servletRequest, String headerKey, String headerValue){
        try {
            logger.info("HttpServletRequest implement class is {}", servletRequest.getClass().getName());
            //获取org.apache.catalina.connector.RequestFacade类中request字段
            Field requestField = servletRequest.getClass().getDeclaredField("request");
            //设置字段跳过安全检查
            requestField.setAccessible(true);
            //获取javax.servlet.http.HttpServletRequest实现类中request字段对象:org.apache.catalina.connector.Request
            Object requestObject = requestField.get(servletRequest);

            logger.info("RequestFacade class filed request implement class is {}.", requestObject.getClass().getName());
            //获取org.apache.catalina.connector.Request类中coyoteRequest字段
            Field coyoteRequestField = requestObject.getClass().getDeclaredField("coyoteRequest");
            //设置字段跳过安全检查
            coyoteRequestField.setAccessible(true);
            //获取org.apache.catalina.connector.Request实现类中coyoteRequest字段对象：org.apache.catalina.connector.Request.coyoteRequest
            Object coyoteRequestObject = coyoteRequestField.get(requestObject);

            logger.info("org.apache.coyote.Request class filed coyoteRequest implement class is {}", coyoteRequestObject.getClass().getName());
            //获取org.apache.catalina.connector.Request.coyoteRequest类中headers字段
            Field headersField = coyoteRequestObject.getClass().getDeclaredField("headers");
            //设置字段跳过安全检查
            headersField.setAccessible(true);
            //获取org.apache.catalina.connector.Request.coyoteRequest实现类中headers字段对象实现：org.apache.tomcat.util.http.MimeHeaders
            Object headersObject = headersField.get(coyoteRequestObject);
            //确定字段类型为：org.apache.tomcat.util.http.MimeHeaders
            if(headersObject instanceof MimeHeaders){
                //使用MimeHeaders类方法设置Header值
                MimeHeaders headers = (MimeHeaders) headersObject;
                //addValue只添加，setValue会遍历查找，如果不存在此Header再添加
//                headers.addValue(headerKey).setString(headerValue);
                headers.setValue(headerKey).setString(headerValue);
                logger.info("update request Headers success:{}", servletRequest.getHeader(headerKey));
            }
        }catch (Exception e){
            logger.error("Update Request Headers Exception; {}", e.getMessage(), e);
            return 1;
        }
        return 0;
    }


}
