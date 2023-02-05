package org.example;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.example.Annotations.Bean;
import org.example.Annotations.Component;
import org.example.Annotations.RestController;
import org.example.DependencyInjector.Container;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.Properties;

public class SpringApplication {
    static Container container;
    static Configuration configuration;
    public static Context run(Class<?> configClass) throws Exception {
        Tomcat tomcat = new Tomcat();
        String docBase = new File(".").getAbsolutePath();
        Context context = tomcat.addContext("/api", docBase);
        String basePath = System.getProperty("user.dir") + "/src/main/java/" + configClass.getPackageName().replace(".", "/");
        configuration = createMyBatisConfig();
        container = new Container();
        ApplicationContext applicationContext = scanComponents(basePath, configClass.getPackageName());
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

    private static ApplicationContext scanComponents(String basePath, String _package) throws Exception {
        List<URL> urls = new ArrayList<>();
        List<String> classNames = new ArrayList<>();
        Path base = Path.of(basePath);
        Files.walkFileTree(base, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".java")) {
                    StringBuilder classPackages = new StringBuilder();
                    String packagesAndClass = base.relativize(file).toString().replace("\\", ".");
                    packagesAndClass = packagesAndClass.substring(0, packagesAndClass.length() - 5);
                    classPackages.append(_package).append(".").append(packagesAndClass);
                    classNames.add(classPackages.toString());
                    urls.add(file.toUri().toURL());
                }
                return FileVisitResult.CONTINUE;
            };
        });
        URLClassLoader classLoader = new URLClassLoader(urls.toArray(new URL[urls.size()]));
        ApplicationContext applicationContext = new ApplicationContext();
        for (String className : classNames) {
            Class<?> clazz = classLoader.loadClass(className);
            RestController restController = clazz.getAnnotation(RestController.class);
            Component component = clazz.getAnnotation(Component.class);
            Mapper mapper = clazz.getAnnotation(Mapper.class);
            if (restController != null)
                applicationContext.registerController(clazz);
            else if (component != null)
                applicationContext.registerComponent(clazz);
            else if (mapper != null)
                registerMapper(clazz);
        }
        classLoader.close();
        return applicationContext;
    }

    private static Configuration createMyBatisConfig() throws IOException {
        Properties properties = new Properties();
        InputStream inputStream = SpringApplication.class.getClassLoader().getResourceAsStream("application.properties");
        properties.load(inputStream);
        String url = properties.getProperty("url");
        String username = properties.getProperty("username");
        String password = properties.getProperty("password");
        String driver = "com.mysql.jdbc.Driver";
        PooledDataSource pooledDataSource = new PooledDataSource();
        pooledDataSource.setUrl(url);
        pooledDataSource.setUsername(username);
        pooledDataSource.setPassword(password);
        pooledDataSource.setDriver(driver);
        JdbcTransactionFactory transactionFactory = new JdbcTransactionFactory();
        Environment environment = new Environment("env", transactionFactory, pooledDataSource);
        return new Configuration(environment);
    }

    private static void registerMapper(Class<?> mapperClass) throws Exception {
        configuration.addMapper(mapperClass);
        SqlSessionFactory sessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        SqlSession session = sessionFactory.openSession(true);
        Object mapper = session.getMapper(mapperClass);
        container.registerInstance(mapperClass, mapper);
    }
}