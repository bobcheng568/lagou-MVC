package com.lagou.demo.controller;

import com.lagou.demo.service.IDemoService;
import com.lagou.edu.mvcframework.annotations.LagouAutowired;
import com.lagou.edu.mvcframework.annotations.LagouController;
import com.lagou.edu.mvcframework.annotations.LagouRequestMapping;
import com.lagou.edu.mvcframework.annotations.Security;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@LagouController
@LagouRequestMapping("/demo")
@Security({"zhangsan"})
public class DemoController {


    @LagouAutowired
    private IDemoService demoService;


    /**
     * URL: /demo/query?name=lisi
     * @param request
     * @param response
     * @param name
     * @return
     */
    @LagouRequestMapping("/query")
    public String query(HttpServletRequest request, HttpServletResponse response,String name) {
        System.out.println("controller 实现类中的name参数：" + name) ;
        return demoService.get(name);
    }

    @LagouRequestMapping("/validate")
    @Security({"wangwu"})
    public String validate(HttpServletRequest request, HttpServletResponse response,String name) {
        System.out.println("controller 实现类中的name参数：" + name) ;
        return demoService.get(name);
    }
}
