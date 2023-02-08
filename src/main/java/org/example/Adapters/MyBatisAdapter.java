package org.example.Adapters;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.example.AnnotationApplicationContext;
import org.example.DependencyInjector.Annotations.Inject;
import org.example.SpringApplication;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Properties;

public class MyBatisAdapter {
    Configuration configuration;
    SqlSessionFactory sqlSessionFactory;
    AnnotationApplicationContext applicationContext;

    @Inject
    public MyBatisAdapter(AnnotationApplicationContext applicationContext) throws Exception {
        this.applicationContext = applicationContext;
        this.configuration = createMyBatisConfig();
        this.sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        initializeMappers();
    }

    private Configuration createMyBatisConfig() throws IOException {
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

    public void initializeMappers() throws Exception {
        List<Class<?>> components = applicationContext.getClasses();
        for (Class<?> mapperClass : components) {
            if (!mapperClass.isAnnotationPresent(Mapper.class))
                continue;

            Object mapper = createMapperInstance(mapperClass);
            applicationContext.registerInstance(mapperClass, mapper);
        }
    }

    private Object createMapperInstance(Class<?> mapperClass) {
        configuration.addMapper(mapperClass);
        return createMapperProxy(mapperClass);
    }

    private <T> T createMapperProxy(Class<T> mapperClass) {
        return (T) Proxy.newProxyInstance(MyBatisAdapter.class.getClassLoader(), new Class<?>[] {mapperClass}, new MapperHandler(mapperClass));
    }

    private class MapperHandler implements InvocationHandler {
        private Class<?> mapperClass;
        MapperHandler(Class<?> mapperClass) {
            this.mapperClass = mapperClass;
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            SqlSession session = sqlSessionFactory.openSession(true);
            Object mapper = session.getMapper(mapperClass);
            Object result = method.invoke(mapper, args);
            session.close();
            return result;
        }
    }
}