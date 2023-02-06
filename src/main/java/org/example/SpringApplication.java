package org.example;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.ibatis.annotations.Mapper;
import org.example.Annotations.*;
import org.example.DependencyInjector.Container;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class SpringApplication {
    static Container container;
    static MyBatisAdapter myBatisAdapter;
    public static Context run(Class<?> configClass) throws Exception {
        Tomcat tomcat = new Tomcat();
        myBatisAdapter = new MyBatisAdapter();
        container = new Container();
        String docBase = new File(".").getAbsolutePath();
        Context context = tomcat.addContext("", docBase);
        String root = System.getProperty("user.dir") + "/src/main/java/";
        String basePath = root + configClass.getPackageName().replace(".", "/");
        ComponentScan[] packages = configClass.getDeclaredAnnotationsByType(ComponentScan.class);
        ApplicationContext applicationContext = scanComponents(basePath, configClass.getPackageName(), packages);
        registerConfigBeans(configClass);
        DispatcherServlet dispatcherServlet = new DispatcherServlet(applicationContext, container);
        Tomcat.addServlet(context, "DispatcherServlet", dispatcherServlet);
        context.addServletMappingDecoded("/*", "DispatcherServlet");

        tomcat.start();
        tomcat.getServer().await();
        return context;
    }

    private static void registerConfigBeans(Class<?> configClass) throws Exception {
        Method[] methods = configClass.getDeclaredMethods();
        Object config = configClass.getDeclaredConstructor().newInstance();
        for (Method method : methods) {
            Bean bean = method.getAnnotation(Bean.class);
            if (bean == null)
                continue;
            Class<?> beanClass = method.getReturnType();
            Object beanInstance = method.invoke(config);
            container.registerInstance(beanClass, beanInstance);
        }
    }

    private static ApplicationContext scanComponents(String basePath, String _package, ComponentScan[] packages) throws Exception {
        List<URL> urls = new ArrayList<>();
        List<String> classNames = new ArrayList<>();
        Path base = Path.of(basePath);
        walkPackage(_package, urls, classNames, base);
        for (ComponentScan annotation : packages) {
            String additionalPackage = annotation.value().replace("\\", ".");
            Path root = Path.of(System.getProperty("user.dir"), "/src/main/java/", additionalPackage);
            walkPackage(_package, urls, classNames, root);
        }
        URLClassLoader classLoader = new URLClassLoader(urls.toArray(new URL[urls.size()]));
        ApplicationContext context = initializeApplicationContext(classNames, classLoader);
        classLoader.close();
        return context;
    }

    private static void walkPackage(String _package, List<URL> urls, List<String> classNames, Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".java")) {
                    StringBuilder classPackages = new StringBuilder();
                    String packagesAndClass = root.relativize(file).toString().replace("\\", ".");
                    packagesAndClass = packagesAndClass.substring(0, packagesAndClass.length() - 5);
                    classPackages.append(_package).append(".").append(packagesAndClass);
                    classNames.add(classPackages.toString());
                    urls.add(file.toUri().toURL());
                }
                return FileVisitResult.CONTINUE;
            };
        });
    }

    private static ApplicationContext initializeApplicationContext(List<String> classNames, ClassLoader classLoader) throws Exception {
        ApplicationContext applicationContext = new ApplicationContext();
        for (String className : classNames) {
            Class<?> clazz = classLoader.loadClass(className);
            RestController restController = clazz.getAnnotation(RestController.class);
            Controller controller = clazz.getAnnotation(Controller.class);
            Component component = clazz.getAnnotation(Component.class);
            Mapper mapper = clazz.getAnnotation(Mapper.class);
            if (restController != null && !clazz.isInterface())
                applicationContext.registerController(clazz, clazz, true);

            else if (controller != null && !clazz.isInterface())
                applicationContext.registerController(clazz, clazz, false);

            else if (component != null) {
                Class<?>[] interfaces = clazz.getInterfaces();
                for (Class<?> _interface : interfaces) {
                    RestController interfaceRestAnn = _interface.getAnnotation(RestController.class);
                    if (interfaceRestAnn != null) {
                        applicationContext.registerController(clazz, _interface, true);
                    }
                    Controller interfaceControllerAnn = _interface.getAnnotation(Controller.class);
                    if (interfaceControllerAnn != null) {
                        applicationContext.registerController(clazz, _interface, false);
                    }
                    break;
                }
            }

            else if (mapper != null) {
                Object mapperInstance = myBatisAdapter.createMapperInstance(clazz);
                container.registerInstance(clazz, mapperInstance);
            }
        }
        return applicationContext;
    }
}