package com.lagou.edu.mvcframework.servlet;

import com.google.common.collect.Lists;
import com.lagou.edu.mvcframework.annotations.LagouAutowired;
import com.lagou.edu.mvcframework.annotations.LagouController;
import com.lagou.edu.mvcframework.annotations.LagouRequestMapping;
import com.lagou.edu.mvcframework.annotations.LagouService;
import com.lagou.edu.mvcframework.interceptor.LagouHandlerInterceptor;
import com.lagou.edu.mvcframework.pojo.HandlerMapping;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class LgDispatcherServlet extends HttpServlet {

    private Properties properties = new Properties();

    private List<String> classNames = new ArrayList<>(); // 缓存扫描到的类的全限定类名

    // ioc容器
    private Map<String, Object> ioc = new HashMap<String, Object>();

    private List<HandlerMapping> handlerMappings = new ArrayList<>();

    private List<LagouHandlerInterceptor> interceptors = new ArrayList<>();

    @Override
    public void init(ServletConfig config) throws ServletException {
        // 1 加载配置文件 springmvc.properties
        String contextConfigLocation = config.getInitParameter("contextConfigLocation");
        doLoadConfig(contextConfigLocation);

        // 2 扫描相关的类，扫描注解
        doScan(properties.getProperty("scanPackage"));

        // 3 初始化bean对象（实现ioc容器，基于注解）
        doInstance();

        // 4 实现依赖注入
        doAutoWired();

        // 5 构造一个HandlerMapping处理器映射器，将配置好的url和Method建立映射关系
        initHandlerMapping();

        System.out.println("lagou mvc 初始化完成....");

        // 等待请求进入，处理请求
    }

    /*
        构造一个HandlerMapping处理器映射器
        最关键的环节
        目的：将url和method建立关联
     */
    private void initHandlerMapping() {
        if (ioc.isEmpty()) {
            return;
        }
        ioc.values().stream()
                .filter(instance -> instance.getClass().isAnnotationPresent(LagouController.class))
                .forEach(instance -> {
                    Class<?> aClass = instance.getClass();
                    String baseUrl = "";
                    if (aClass.isAnnotationPresent(LagouRequestMapping.class)) {
                        LagouRequestMapping annotation = aClass.getAnnotation(LagouRequestMapping.class);
                        baseUrl = annotation.value(); // 等同于/demo
                    }
                    String finalBaseUrl = baseUrl;
                    Stream.of(aClass.getMethods())
                            .filter(method -> method.isAnnotationPresent(LagouRequestMapping.class))
                            .forEach(method -> {
                                LagouRequestMapping annotation = method.getAnnotation(LagouRequestMapping.class);
                                String methodUrl = annotation.value();  // /query
                                String url = finalBaseUrl + methodUrl;    // 计算出来的url /demo/query
                                // 把method所有信息及url封装为一个Handler
                                HandlerMapping handlerMapping = new HandlerMapping(instance, method, Pattern.compile(url));
                                // 计算方法的参数位置信息  // query(HttpServletRequest request, HttpServletResponse response,String name)
                                Parameter[] parameters = method.getParameters();
                                IntStream.range(0, method.getParameters().length).forEach(j -> {
                                    Parameter parameter = parameters[j];
                                    if (parameter.getType() == HttpServletRequest.class || parameter.getType() == HttpServletResponse.class) {
                                        // 如果是request和response对象，那么参数名称写HttpServletRequest和HttpServletResponse
                                        handlerMapping.getParamIndexMapping().put(parameter.getType().getSimpleName(), j);
                                        return;
                                    }
                                    handlerMapping.getParamIndexMapping().put(parameter.getName(), j);  // <name,2>
                                });
                                handlerMapping.getInterceptors().addAll(this.interceptors);
                                // 建立url和method之间的映射关系（map缓存起来）
                                handlerMappings.add(handlerMapping);
                            });
                });
    }

    //  实现依赖注入
    private void doAutoWired() {
        if (ioc.isEmpty()) {
            return;
        }
        // 遍历ioc中所有对象，查看对象中的字段，是否有@LagouAutowired注解，如果有需要维护依赖注入关系
        ioc.forEach((key, instance) -> Stream.of(instance.getClass().getDeclaredFields())
                .filter(declaredField -> declaredField.isAnnotationPresent(LagouAutowired.class))
                .forEach(declaredField -> {
                    LagouAutowired annotation = declaredField.getAnnotation(LagouAutowired.class);
                    String beanName = StringUtils.isNotBlank(annotation.value()) ? annotation.value() : declaredField.getType().getName();
                    // 开启赋值
                    declaredField.setAccessible(true);
                    try {
                        declaredField.set(instance, ioc.get(beanName));
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }));
    }


    // ioc容器
    // 基于classNames缓存的类的全限定类名，以及反射技术，完成对象创建和管理
    private void doInstance() {
        if (classNames.size() == 0) {
            return;
        }
        classNames.forEach(className -> {
            try {
                // 反射
                Class<?> aClass = Class.forName(className);
                if (aClass.isAnnotationPresent(LagouController.class)) {
                    // controller的id此处不做过多处理，不取value了，就拿类的首字母小写作为id，保存到ioc中
                    ioc.put(lowerFirst(aClass.getSimpleName()), aClass.newInstance());
                    return;
                }
                if (aClass.isAnnotationPresent(LagouService.class)) {
                    LagouService annotation = aClass.getAnnotation(LagouService.class);
                    //获取注解value值
                    String beanName = StringUtils.isNotBlank(annotation.value()) ? annotation.value() : lowerFirst(aClass.getSimpleName());
                    ioc.put(beanName, aClass.newInstance());
                    // service层往往是有接口的，面向接口开发，此时再以接口名为id，放入一份对象到ioc中，便于后期根据接口类型注入
                    Stream.of(aClass.getInterfaces()).forEach(anInterface -> {
                        try {
                            // 以接口的全限定类名作为id放入
                            ioc.put(anInterface.getName(), aClass.newInstance());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                    return;
                }
                if (Lists.newArrayList(aClass.getInterfaces()).contains(LagouHandlerInterceptor.class)
                        && !Modifier.isAbstract(aClass.getModifiers())) {
                    interceptors.add((LagouHandlerInterceptor) aClass.newInstance());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }


    // 首字母小写方法
    private String lowerFirst(String str) {
        char[] chars = str.toCharArray();
        if ('A' <= chars[0] && chars[0] <= 'Z') {
            chars[0] += 32;
        }
        return String.valueOf(chars);
    }


    // 扫描类
    // scanPackage: com.lagou.demo  package---->  磁盘上的文件夹（File）  com/lagou/demo
    private void doScan(String scanPackage) {
        String scanPackagePath = Thread.currentThread().getContextClassLoader().getResource("").getPath() + scanPackage.replaceAll("\\.", "/");
        File pack = new File(scanPackagePath);
        Stream.of(Objects.requireNonNull(pack.listFiles())).forEach(file -> {
            if (file.getName().endsWith(".class")) {
                classNames.add(scanPackage + "." + file.getName().replaceAll(".class", ""));
                return;
            }
            if (file.isDirectory()) {
                doScan(scanPackage + "." + file.getName());
            }
        });
    }

    // 加载配置文件
    private void doLoadConfig(String contextConfigLocation) {
        InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            properties.load(resourceAsStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }


    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // 根据uri获取到能够处理当前请求的hanlder（从handlermapping中（list））
        HandlerMapping handlerMapping = getHandler(req);
        if (handlerMapping == null) {
            resp.getWriter().write("404 not found");
            return;
        }

        // 参数绑定
        // 获取所有参数类型数组，这个数组的长度就是我们最后要传入的args数组的长度
        Class<?>[] parameterTypes = handlerMapping.getMethod().getParameterTypes();

        // 根据上述数组长度创建一个新的数组（参数数组，是要传入反射调用的）
        Object[] paraValues = new Object[parameterTypes.length];

        // 以下就是为了向参数数组中塞值，而且还得保证参数的顺序和方法中形参顺序一致
        Map<String, String[]> parameterMap = req.getParameterMap();

        // 遍历request中所有参数  （填充除了request，response之外的参数）
        for (Map.Entry<String, String[]> param : parameterMap.entrySet()) {
            // name=1&name=2   name [1,2]
            String value = StringUtils.join(param.getValue(), ",");  // 如同 1,2
            // 如果参数和方法中的参数匹配上了，填充数据
            if (!handlerMapping.getParamIndexMapping().containsKey(param.getKey())) {
                continue;
            }
            // 方法形参确实有该参数，找到它的索引位置，对应的把参数值放入paraValues
            Integer index = handlerMapping.getParamIndexMapping().get(param.getKey());//name在第 2 个位置
            paraValues[index] = value;  // 把前台传递过来的参数值填充到对应的位置去
        }

        int requestIndex = handlerMapping.getParamIndexMapping().get(HttpServletRequest.class.getSimpleName()); // 0
        paraValues[requestIndex] = req;

        int responseIndex = handlerMapping.getParamIndexMapping().get(HttpServletResponse.class.getSimpleName()); // 1
        paraValues[responseIndex] = resp;

        // 最终调用handler的method属性
        try {
            if (!handlerMapping.applyPreHandle(req, resp)) {
                return;
            }
            handlerMapping.getMethod().invoke(handlerMapping.getController(), paraValues);
            handlerMapping.applyPostHandle(req, resp, null);
            handlerMapping.triggerAfterCompletion(req, resp, null);
        } catch (Exception e) {
            e.printStackTrace();
            try {
                handlerMapping.triggerAfterCompletion(req, resp, e);
            } catch (Exception e1) {
                throw new RuntimeException(e1);
            }
        }
    }

    private HandlerMapping getHandler(HttpServletRequest req) {
        if (handlerMappings.isEmpty()) {
            return null;
        }
        String url = req.getRequestURI();
        for (HandlerMapping handlerMapping : handlerMappings) {
            Matcher matcher = handlerMapping.getPattern().matcher(url);
            if (!matcher.matches()) {
                continue;
            }
            return handlerMapping;
        }
        return null;
    }


}
