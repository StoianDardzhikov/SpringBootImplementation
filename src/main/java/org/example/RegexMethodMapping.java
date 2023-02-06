package org.example;

import org.example.Annotations.PathVariable;
import org.example.Annotations.RequestBody;
import org.example.Annotations.RequestParam;

import javax.servlet.ServletException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexMethodMapping extends MethodMapping {
    Matcher matcher;
    Pattern regexPattern;
    String method;

    public RegexMethodMapping(MethodMapping methodMapping) {
        super(methodMapping.controller, methodMapping.method, methodMapping.isResponseBody);
        parameterIndexes = methodMapping.parameterIndexes;
    }

    public void setPattern(String pattern) {
        this.regexPattern = Pattern.compile(pattern);
    }

    public void setMethod(String method) {
        this.method = method;
    }


    public boolean isMatchingUrl(String url, String method) {
        this.matcher = regexPattern.matcher(url);
        return method.equals(this.method) && matcher.matches();
    }

    protected List<Object> initializeMethodParameters() throws ServletException {
        List<Object> parsedParameters = new ArrayList<>();
        Method controllerMethod = super.method;
        Parameter[] parameters = controllerMethod.getParameters();
        for (Parameter parameter : parameters) {
            RequestBody requestBody = parameter.getAnnotation(RequestBody.class);
            if (requestBody != null) {
                if (method.equals("GET") || method.equals("DELETE"))
                    throw new ServletException("Request body not supported on GET and DELETE methods!");

                parsedParameters.add(super.requestBody);
                continue;
            }

            RequestParam requestParamAnn = parameter.getAnnotation(RequestParam.class);
            if (requestParamAnn != null) {
                Object reqParam = requestParams.get(parameter.getName());
                parsedParameters.add(reqParam);
                continue;
            }

            PathVariable pathVariable = parameter.getAnnotation(PathVariable.class);
            if (pathVariable == null)
                continue;

            Integer regexIndex = parameterIndexes.get(parameter.getName());
            if (regexIndex == null)
                throw new ServletException("Method parameter \""+ parameter.getName() + "\" was not found in the request!");
            String pathVar = matcher.group(regexIndex);
            Object objPathVar = parsePathVariable(pathVar, parameter.getType());
            parsedParameters.add(objPathVar);
        }

        return parsedParameters;
    }

    private Object parsePathVariable(String pathVar, Class<?> parseClass) {
        if (parseClass.equals(Integer.class) || parseClass.equals(int.class))
            return Integer.parseInt(pathVar);

        if (parseClass.equals(Double.class) || parseClass.equals(double.class))
            return Double.parseDouble(pathVar);

        if (parseClass.equals(Long.class) || parseClass.equals(long.class))
            return Long.parseLong(pathVar);

        if (parseClass.equals(Short.class) || parseClass.equals(short.class))
            return Short.parseShort(pathVar);

        if (parseClass.equals(Float.class) || parseClass.equals(float.class))
            return Float.parseFloat(pathVar);

        if (parseClass.equals(Boolean.class) || parseClass.equals(boolean.class))
            return Boolean.parseBoolean(pathVar);

        return pathVar;
    }

    @Override
    public Object invoke(Object instance) throws InvocationTargetException, IllegalAccessException, ServletException {
        List<Object> parameters = initializeMethodParameters();
        return super.method.invoke(instance, parameters.toArray(new Object[0]));
    }

    @Override
    public String toString() {
        return regexPattern + " " + method;
    }
}
