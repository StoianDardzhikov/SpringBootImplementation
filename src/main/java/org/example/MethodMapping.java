package org.example;

import org.example.Annotations.RequestBody;
import org.example.Annotations.RequestParam;

import javax.servlet.ServletException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MethodMapping {
    Class<?> controller;
    Method method;
    Map<String, Integer> parameterIndexes;
    Map<String, Object> requestParams;
    Object requestBody;
    Class<?> requestBodyType;
    public MethodMapping(Class<?> controller, Method method) {
        this.controller = controller;
        this.method = method;
        this.parameterIndexes = new HashMap<>();
        this.requestBodyType = findRequestBodyType();
        this.requestParams = new HashMap<>();
    }

    protected List<Object> initializeMethodParameters() throws ServletException {
        List<Object> parsedParameters = new ArrayList<>();
        Parameter[] parameters = method.getParameters();
        for (Parameter parameter : parameters) {
            RequestBody requestBodyAnn = parameter.getAnnotation(RequestBody.class);
            if (requestBodyAnn != null) {
                parsedParameters.add(requestBody);
                continue;
            }

            RequestParam requestParamAnn = parameter.getAnnotation(RequestParam.class);
            if (requestParamAnn != null) {
                Object reqParam = requestParams.get(parameter.getName());
                parsedParameters.add(reqParam);
            }
        }

        return parsedParameters;
    }

    public Class<?> getRequestBodyType() {
        return requestBodyType;
    }

    public void addRequestParam(String name, Object obj) {
        requestParams.put(name, obj);
    }

    public void addParameter(String name, int i) {
        parameterIndexes.put(name, i);
    }

    public void setRequestBody(Object requestBody) {
        this.requestBody = requestBody;
    }

    public Object invoke(Object instance) throws InvocationTargetException, IllegalAccessException, ServletException {
        List<Object> parameters = initializeMethodParameters();
        return method.invoke(instance, parameters.toArray(new Object[0]));
    }

    Class<?> findRequestBodyType() {
        Parameter[] parameters = method.getParameters();
        for (Parameter parameter : parameters) {
            if (parameter.getAnnotation(RequestBody.class) != null)
                return parameter.getType();
        }
        return null;
    }
}