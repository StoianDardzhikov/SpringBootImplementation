package org.example;

import org.example.Adapters.MyBatisAdapter;
import org.example.Adapters.TomcatAdapter;

public class SpringApplication {
    public static AnnotationApplicationContext run(Class<?> configClass) throws Exception {
        return new AnnotationApplicationContext(configClass);
    }
}