package org.example.DependencyInjector;

import org.example.DependencyInjector.Annotations.Value;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BeanFactoryPostProcessor {

    private static Pattern propertyPattern = Pattern.compile("\\${([a-zA-Z0-9]+)}");

    HashMap<String, Object> properties = new HashMap<>();

    public BeanFactoryPostProcessor(Properties properties) {
        properties.putAll(this.properties);
    }

    public void processProperties(Object bean) throws ContainerException, IllegalAccessException {
        Class<?> beanClass = bean.getClass();
        Field[] fields = beanClass.getDeclaredFields();
        for (Field field : fields) {
            Value value = field.getAnnotation(Value.class);
            if (value == null)
                continue;

            String propValue = value.value();
            Matcher matcher;
            if(!(matcher = propertyPattern.matcher(propValue)).matches())
                throw new ContainerException("Invalid property format. Property must be written as ${property_name}");

            String propName = matcher.group(1);
            Object property = properties.get(propName);
            if (property == null)
                throw new ContainerException("No property with name " + propName + " was found!");

            field.set(bean, property);
        }
    }
}
