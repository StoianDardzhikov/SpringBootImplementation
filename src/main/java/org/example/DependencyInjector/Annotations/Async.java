package org.example.DependencyInjector.Annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Async {
    String value() default "applicationThreadPoolExecutor";
}