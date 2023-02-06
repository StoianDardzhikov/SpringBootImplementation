package org.example.DependencyInjector;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import org.example.DependencyInjector.Annotations.*;
import org.example.DependencyInjector.Annotations.EventListener;
import org.mockito.Mockito;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Container {
    public static int DEFAULT_NUMBER_OF_THREADS_FOR_ASYNC_METHODS = 16;

    HashMap<String, Object> keyInstances = new HashMap<>();
    HashMap<Class<?>, Object> classInstances = new HashMap<>();
    HashMap<Class<?>, Class<?>> implementations = new HashMap<>();
    HashSet<Method> asyncMethods = new HashSet<>();
    HashMap<String, ExecutorService> threadPoolExecutors = new HashMap<>();
    BeanFactoryPostProcessor beanFactoryPostProcessor;
    ApplicationEventPublisher applicationEventPublisher;
    boolean isAsync;

    public Container() {
        registerEventPublisher();
    }

    public Container(Class<?> configClass) throws IOException {
        this();
        EnableAsync enableAsync = configClass.getAnnotation(EnableAsync.class);
        if (enableAsync != null) {
            isAsync = true;
            threadPoolExecutors.put("applicationThreadPoolExecutor", Executors.newFixedThreadPool(DEFAULT_NUMBER_OF_THREADS_FOR_ASYNC_METHODS));
        }
        PropertiesSource propertiesSource = configClass.getAnnotation(PropertiesSource.class);
        if (propertiesSource == null)
            return;
        String fileName = propertiesSource.value();
        Properties properties = new Properties();
        properties.loadFromXML(new FileInputStream(fileName));
        beanFactoryPostProcessor = new BeanFactoryPostProcessor(properties);
    }

    private void registerEventPublisher() {
        applicationEventPublisher = new ApplicationEventPublisher();
        classInstances.put(ApplicationEventPublisher.class, applicationEventPublisher);
    }

    public Object getInstance(String key) {
        return keyInstances.get(key);
    }

    public <T> T getInstance(Class<T> c) throws Exception {
        Class<?> keyClass = c;
        T existingInstance = (T) classInstances.get(c);
        if (existingInstance != null)
            return existingInstance;
        if (c.isInterface()) {
            keyClass = getInterfaceImplementationClass(c);
        }
        T instance = (T) classInstances.get(keyClass);
        if (instance == null) {
            instance = createProxy(c, true);
            decorateInstance(keyClass, instance, true, new HashSet<>());
        }
        classInstances.put(keyClass, instance);

        return instance;
    }

    public void decorateInstance(Object o) throws Exception {
        addFields(o.getClass(), o, false, new HashSet<>());
    }

    public void registerInstance(String key, Object instance) throws ContainerException {
        if (keyInstances.containsKey(key))
            throw new ContainerException("This key is already used for another instance");
        keyInstances.put(key, instance);
    }

    public <T> void registerInstance(Class<T> c, Object instance) throws Exception {
        if (classInstances.containsKey(c))
            throw new ContainerException("This class is already used for another instance");
        classInstances.put(c, instance);
    }

    public <T, U> void registerImplementation(Class<T> ifs, Class<U> impl) throws ContainerException {
        if (implementations.containsKey(ifs))
            throw new ContainerException("This interface already has implementing class registered");
        implementations.put(ifs, impl);
    }
    public void registerInstance(Object instance) throws Exception {
        registerInstance(instance.getClass(), instance);
    }

    private <T> T constructProxy(Class<T> c, boolean addToContainer, Enhancer enhancer) throws Exception {
        Constructor<?>[] constructors = c.getDeclaredConstructors();
        Constructor<?> constructor = null;
        Object instance = null;
        for (Constructor constr : constructors) {
            Inject inject = (Inject) constr.getAnnotation(Inject.class);
            if (inject != null) {
                constructor = constr;
                break;
            }
        }

        if (constructor == null)
            instance = enhancer.create();
        else {
            constructor.getParameters()[0].getName();
            Parameter[] parameters = constructor.getParameters();
            Class<?>[] types = new Class[parameters.length];
            Object[] objects = new Object[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                NamedParameter namedParameter = parameters[i].getAnnotation(NamedParameter.class);
                if (namedParameter != null) {
                    String name = namedParameter.value();
                    Object parameter = keyInstances.get(name);
                    if (parameter != null) {
                        objects[i] = parameter;
                        continue;
                    }
                }
                types[i]  = parameters[i].getType();
                objects[i] = getInstance(parameters[i].getType());
            }

            instance = enhancer.create(types, objects);
        }

        if (addToContainer)
            classInstances.put(c, instance);

        return (T) instance;
    }

    private <T> void decorateInstance(Class<T> c, Object instance, boolean addToContainer, HashSet<Class<?>> passedClasses) throws Exception {
        passedClasses.add(c);
        addFields(c, instance, addToContainer, passedClasses);
        if (beanFactoryPostProcessor != null)
            beanFactoryPostProcessor.processProperties(instance);
        if (Initializer.class.isAssignableFrom(c)) {
            invokeInit(instance);
        }
    }

    private <T> void addFields(Class<T> c, Object instance, boolean addToContainer, HashSet<Class<?>> passedClasses) throws Exception {
        Field[] declaredFields = c.getDeclaredFields();
        for (Field field : declaredFields) {
            Inject inject = field.getAnnotation(Inject.class);
            if (inject == null)
                continue;
            Named named = field.getAnnotation(Named.class);
            if (named != null) {
                String fieldName = field.getName();
                Object fieldKeyInstance = keyInstances.get(fieldName);
                if (fieldKeyInstance != null) {
                    field.set(instance, fieldKeyInstance);
                    continue;
                }
            }
            Class<?> fieldType = field.getType();
            if (passedClasses.contains(fieldType)) {
                Object fieldInstance = classInstances.get(fieldType);
                if(fieldInstance != null) {
                    field.set(fieldInstance, instance);
                    continue;
                }
                throw new ContainerException("Circular dependency found!");
            }

            if (fieldType.isInterface()) {
                Object implementationInstance = getInterfaceImplementation(fieldType);
                field.set(instance, implementationInstance);
                continue;
            }
            Object fieldClassInstance = classInstances.get(fieldType);
            if (fieldClassInstance != null) {
                field.set(instance, fieldClassInstance);
                continue;
            }
            fieldClassInstance = getInstance(fieldType);
            Lazy lazy = field.getAnnotation(Lazy.class);
            if (lazy != null) {
                fieldClassInstance = createMock(fieldType, field, instance);
            }
            field.set(instance, fieldClassInstance);
        }
    }

    private <T> T createMock(Class<T> mockClass, Field field, Object parent) {
        T mock = Mockito.mock(mockClass);
        Mockito.mock(mockClass, invocation -> {
            T instance = createProxy(mockClass, true);
            field.set(parent, instance);
            return invocation;
        });

        return mock;
    }

    private void invokeInit(Object instance) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class<?> c = instance.getClass();
        Method method = c.getMethod("init");
        method.invoke(instance);
    }

    private Object getInterfaceImplementation(Class<?> interfaceClass) throws Exception {
        Object instance = classInstances.get(interfaceClass);
        if (instance != null)
            return instance;
        Class<?> interfaceImplementationClass = getInterfaceImplementationClass(interfaceClass);
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(interfaceImplementationClass);
        return constructProxy(interfaceImplementationClass, false, enhancer);
    }

    private Class<?> getInterfaceImplementationClass(Class<?> interfaceClass) throws Exception {
        Class<?> implementationInstanceClass = implementations.get(interfaceClass);
        if (implementationInstanceClass != null)
            return implementationInstanceClass;

        Default _default = interfaceClass.getAnnotation(Default.class);
        if (_default == null)
            throw new ContainerException("No default implementation for interface: " + interfaceClass.getName());

        return _default.value();
    }

    private void populateListeners(List<Method> listenerMethods, Object instance) throws ContainerException {
        for (Method method : listenerMethods) {
            org.example.DependencyInjector.Annotations.EventListener eventListener = method.getAnnotation(org.example.DependencyInjector.Annotations.EventListener.class);
            if (eventListener != null) {
                Enhancer enhancer = new Enhancer();
                enhancer.setSuperclass(Listener.class);
                enhancer.setCallback((MethodInterceptor) (o, proxyMethod, obj, proxy) -> {
                    Async async = proxyMethod.getAnnotation(Async.class);
                    System.out.println("Invoking on: " + proxyMethod.getName() + " " + method.getName());
                    System.out.println(async);
                    if (async != null) {
                        String executorServiceName = async.value();
                        ExecutorService executorService = threadPoolExecutors.get(executorServiceName);
                        if (executorService == null)
                            throw new ContainerException("Executor service \"" + executorServiceName + "\" not found!");

                        executorService.submit(() -> {
                            try {
                                return proxy.invokeSuper(o, obj);
                            } catch (Throwable e) {
                                throw new RuntimeException(e);
                            }
                        });
                    }
                    return proxy.invokeSuper(o, obj);
                });
                Class<?>[] types = { Object.class, Method.class };
                Object[] args = { instance, method };
                Listener listener = (Listener) enhancer.create(types, args);
                applicationEventPublisher.addListener(listener);
            }
        }
    }

    private <T> T createProxy(Class<T> beanClass, boolean addToContainer) throws Exception {
        Method[] methods = beanClass.getDeclaredMethods();
        List<Method> eventListenerMethods = new ArrayList<>();
        for (Method method : methods) {
            org.example.DependencyInjector.Annotations.EventListener eventListener = method.getAnnotation(EventListener.class);
            if (eventListener != null) {
                eventListenerMethods.add(method);
            }

            Async async = method.getAnnotation(Async.class);
            if (async != null) {
                asyncMethods.add(method);
            }
        }
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(beanClass);
        enhancer.setCallback((MethodInterceptor) (o, method, objects, proxy) -> {
            if (asyncMethods.contains(method)) {
                Async async = method.getAnnotation(Async.class);
                String executorServiceName = async.value();
                ExecutorService executorService = threadPoolExecutors.get(executorServiceName);
                if (executorService == null)
                    throw new ContainerException("Executor service \"" + executorServiceName + "\" not found!");

                Class<?> returnType = method.getReturnType();
                if (returnType.equals(Void.TYPE)) {
                    executorService.execute(() -> {
                        try {
                            proxy.invokeSuper(o, objects);
                        } catch (Throwable e) {
                            throw new RuntimeException(e);
                        }
                    });
                    return null;
                }
                else
                    return executorService.submit(() -> {
                        try {
                            return ((Future) proxy.invokeSuper(o, objects)).get();
                        } catch (Throwable e) {
                            throw new RuntimeException(e);
                        }
                    });
            }
            return proxy.invokeSuper(o, objects);
        });
        T bean = constructProxy(beanClass, addToContainer, enhancer);
        populateListeners(eventListenerMethods, bean);
        return bean;
    }
}