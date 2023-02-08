package org.example;

import org.example.Annotations.*;
import org.example.DependencyInjector.Container;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import java.util.stream.Collectors;

public class AnnotationApplicationContext {
    private static final Path ROOT_PATH = Path.of(System.getProperty("user.dir"), "/target/classes/");
    Container container;
    List<Class<?>> classes;
    List<Class<?>> components = new ArrayList<>();
    public AnnotationApplicationContext(Class<?> applicationClass) throws Exception {
        container = new Container(applicationClass);
        this.classes = loadClasses();
        container.registerInstance(AnnotationApplicationContext.class, this);
        initializeAdapters();
        List<String> packagesToScan = loadConfigurations(applicationClass);
        packagesToScan.add(applicationClass.getPackageName());
        scan(packagesToScan);
    }

    private void initializeAdapters() throws Exception {
        Properties properties = new Properties();
        InputStream inputStream = SpringApplication.class.getClassLoader().getResourceAsStream("application.properties");
        properties.load(inputStream);
        String[] adapterClassNames = properties.getProperty("adapters").split(",");
        for (String adapterClassName : adapterClassNames) {
            Class<?> adapterClass = Class.forName(adapterClassName);
            container.getInstance(adapterClass);
        }
    }

    private List<Class<?>> loadClasses() throws IOException, ClassNotFoundException {
        List<URL> urls = new ArrayList<>();
        List<String> classNames = new ArrayList<>();
        Files.walkFileTree(ROOT_PATH, new SimpleFileVisitor<>() {
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".class")) {
                    String packagesAndClass = ROOT_PATH.relativize(file).toString().replace("\\", ".");
                    packagesAndClass = packagesAndClass.substring(0, packagesAndClass.length() - 6);
                    classNames.add(packagesAndClass);
                    urls.add(file.toUri().toURL());
                }
                return FileVisitResult.CONTINUE;
            }
        });

        List<Class<?>> classes = new ArrayList<>();
        URLClassLoader classLoader = new URLClassLoader(urls.toArray(new URL[urls.size()]));
        for (String className : classNames) {
            Class<?> _class = classLoader.loadClass(className);
            classes.add(_class);
        }
        classLoader.close();

        return classes;
    }

    private List<String> loadConfigurations(Class<?> applicationClass) throws Exception {
       String applicationPackage = applicationClass.getPackageName();
       List<String> packagesToScan = new ArrayList<>();
       for (Class<?> configClass : classes) {
            if (!configClass.getPackageName().startsWith(applicationPackage) ||
                !configClass.isAnnotationPresent(Configuration.class))
                continue;

            registerConfigBeans(configClass);
            ComponentScan[] componentScans = configClass.getDeclaredAnnotationsByType(ComponentScan.class);
            for (ComponentScan componentScan : componentScans) {
                packagesToScan.add(componentScan.value());
            }
            container.getInstance(configClass);
       }

       return packagesToScan;
    }

    public <T> T getInstance(Class<T> clazz) throws Exception {
        return container.getInstance(clazz);
    }

    public <T> void registerInstance(Class<?> instanceClass, T instance) throws Exception {
        container.registerInstance(instanceClass, instance);
    }

    private void registerConfigBeans(Class<?> configClass) throws Exception {
        System.out.println("Registering configuration: " + configClass);
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

    void scan(List<String> packagesToScan) throws Exception {
        for (Class<?> component : classes) {
            if (!startsWithAtLeastOne(component.getPackageName(), packagesToScan) || !isComponent(component))
                continue;

            components.add(component);
        }
    }

    public void registerComponents() throws Exception {
        for (Class<?> component : components) {
            if (component.isInterface())
                continue;

            container.getInstance(component);
        }
    }

    private boolean startsWithAtLeastOne(String _package,  List<String> packages) {
        for (String otherPackage : packages) {
            if (_package.startsWith(otherPackage))
                return true;
        }
        return false;
    }

    private boolean isComponent(Class<?> component) {
        return component.isAnnotationPresent(RestController.class) || component.isAnnotationPresent(Controller.class) || component.isAnnotationPresent(Component.class);
    }

    public List<Class<?>> getClasses() {
        return classes;
    }
}