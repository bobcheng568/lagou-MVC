package com.lagou.edu.mvcframework.pojo;

import com.lagou.edu.mvcframework.interceptor.LagouHandlerInterceptor;
import org.apache.commons.lang3.ObjectUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.List;

/**
 * @author bobcheng
 * @date 2020/4/21
 */
public class LagouHandlerExecutionChain {

    private Method method;

    private List<LagouHandlerInterceptor> interceptors;

    private int interceptorIndex = -1;

    public LagouHandlerExecutionChain(Method method, List<LagouHandlerInterceptor> interceptors) {
        this.method = method;
        this.interceptors = interceptors;
    }

    public boolean applyPreHandle(HttpServletRequest request, HttpServletResponse response) throws Exception {
        List<LagouHandlerInterceptor> interceptors = getInterceptors();
        if (!ObjectUtils.isEmpty(interceptors)) {
            for (int i = 0; i < interceptors.size(); i++) {
                LagouHandlerInterceptor interceptor = interceptors.get(i);
                if (!interceptor.preHandle(request, response, this.method)) {
                    triggerAfterCompletion(request, response, null);
                    return false;
                }
                this.interceptorIndex = i;
            }
        }
        return true;
    }

    public void applyPostHandle(HttpServletRequest request, HttpServletResponse response, ModelAndView mv) throws Exception {
        List<LagouHandlerInterceptor> interceptors = getInterceptors();
        if (!ObjectUtils.isEmpty(interceptors)) {
            for (int i = interceptors.size() - 1; i >= 0; i--) {
                LagouHandlerInterceptor interceptor = interceptors.get(i);
                interceptor.postHandle(request, response, this.method, mv);
            }
        }
    }

    public void triggerAfterCompletion(HttpServletRequest request, HttpServletResponse response, Exception ex)
            throws Exception {
        List<LagouHandlerInterceptor> interceptors = getInterceptors();
        if (!ObjectUtils.isEmpty(interceptors)) {
            for (int i = this.interceptorIndex; i >= 0; i--) {
                LagouHandlerInterceptor interceptor = interceptors.get(i);
                try {
                    interceptor.afterCompletion(request, response, this.method, ex);
                } catch (Throwable ex2) {
                }
            }
        }
    }

    public List<LagouHandlerInterceptor> getInterceptors() {
        return interceptors;
    }

    public void setInterceptors(List<LagouHandlerInterceptor> interceptors) {
        this.interceptors = interceptors;
    }
}
