package org.example;

import org.example.Annotations.Mappings.DeleteMapping;
import org.example.Annotations.Mappings.GetMapping;
import org.example.Annotations.Mappings.PostMapping;
import org.example.Annotations.Mappings.PutMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public class ApplicationContext {
    List<Class<?>> controllers = new ArrayList<>();
    List<Class<?>> components = new ArrayList<>();
    Map<String, MethodMapping> methodMappings = new HashMap<>();
    List<RegexMethodMapping> regexMethodMappings = new ArrayList<>();

    public void registerController(Class<?> controllerClass) {
        controllers.add(controllerClass);
        registerMethodMappingsForController(controllerClass);
    }

    public void registerComponent(Class<?> componentClass) {
        components.add(componentClass);
    }

    public MethodMapping getMethodMapping(String url, String method) {
        MethodMapping methodMapping = methodMappings.get(url + " " + method);
        if (methodMapping != null)
            return methodMapping;
        return getRegexMethodMapping(url, method);
    }

    private Pattern pattern = Pattern.compile("(\\{(.+)})");
    private void registerMethodMappingsForController(Class<?> controllerClass) {
        org.example.Annotations.Mappings.RequestMapping requestMapping = controllerClass.getAnnotation(org.example.Annotations.Mappings.RequestMapping.class);
        String controllerPath = "";
        if (requestMapping != null)
            controllerPath = requestMapping.value();
        Method[] methods = controllerClass.getDeclaredMethods();
        for (Method method : methods) {
            MethodMapping methodMapping = new MethodMapping(controllerClass, method);
            String path = getPathFromMethod(method, methodMapping);
            if (path == null)
                continue;
            if (!isRegex(path))
                methodMappings.put(controllerPath + path, methodMapping);
            else {
                RegexMethodMapping regexMethodMapping = new RegexMethodMapping(methodMapping);
                String[] pathAndMethod = path.split(" ");
                regexMethodMapping.setPattern(controllerPath + pathAndMethod[0]);
                regexMethodMapping.setMethod(pathAndMethod[1]);
                regexMethodMappings.add(regexMethodMapping);
            }
        }
    }

    private boolean isRegex(String path) {
        return path.contains("[");
    }

    private String getPathFromMethod(Method method, MethodMapping methodMapping) {
        GetMapping getMapping = method.getAnnotation(GetMapping.class);
        if (getMapping != null) return createRegex(getMapping.value(), methodMapping) + " GET";

        PostMapping postMapping = method.getAnnotation(PostMapping.class);
        if (postMapping != null) return createRegex(postMapping.value(), methodMapping) + " POST";

        PutMapping putMapping = method.getAnnotation(PutMapping.class);
        if (putMapping != null) return createRegex(putMapping.value(), methodMapping) + " PUT";

        DeleteMapping deleteMapping = method.getAnnotation(DeleteMapping.class);
        if (deleteMapping != null) return createRegex(deleteMapping.value(), methodMapping) + " DELETE";

        return null;
    }

    private String createRegex(String path, MethodMapping methodMapping) {
        path = path.replace("*", "[^\\/]+");
        AtomicInteger i = new AtomicInteger(1);
        String finalPath = path;
        return pattern.matcher(finalPath).replaceAll(matchResult -> {
            String parameterName = finalPath.substring(matchResult.start() + 1, matchResult.end() - 1);
            methodMapping.addParameter(parameterName, i.getAndIncrement());
            return "([^\\/]+)";
        });
    }

    private MethodMapping getRegexMethodMapping(String url, String method) {
        for (RegexMethodMapping regexMethodMapping : regexMethodMappings) {
            if (regexMethodMapping.isMatchingUrl(url, method)) {
                return regexMethodMapping;
            }
        }
        return null;
    }
}