package com.lagou.edu.mvcframework.interceptor;

import com.google.common.collect.Lists;
import com.lagou.edu.mvcframework.annotations.Security;
import com.lagou.edu.mvcframework.pojo.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * @author bobcheng
 * @date 2020/4/20
 */
public class SecurityInterceptor implements LagouHandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        System.out.println("SecurityInterceptor========preHandle");
        if (!handler.getClass().equals(Method.class)) {
            return true;
        }

        Method method = (Method) handler;
        Security security = method.getAnnotation(Security.class);
        if (Objects.isNull(security)) {
            Class<?> declaringClass = method.getDeclaringClass();
            security = declaringClass.getAnnotation(Security.class);
            if (Objects.isNull(security)) {
                return true;
            }
        }

        String[] names = security.value();
        String username = request.getParameter("username");
        boolean pass = Lists.newArrayList(names).contains(username);
        if (!pass) {
            response.getWriter().write("No Access Permission");
        }
        return pass;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        System.out.println("SecurityInterceptor========postHandle");
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        System.out.println("SecurityInterceptor========afterCompletion");
    }

}
