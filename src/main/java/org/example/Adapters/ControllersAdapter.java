package org.example.Adapters;

import org.example.Annotations.Component;
import org.example.Annotations.Controller;
import org.example.Annotations.Mappings.*;
import org.example.Annotations.ResponseBody;
import org.example.Annotations.RestController;
import org.example.MethodMapping;
import org.example.RegexMethodMapping;
import org.example.ResponseEntity;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public class ControllersAdapter {
    List<Class<?>> controllers = new ArrayList<>();
    public Map<String, MethodMapping> methodMappings = new HashMap<>();
    public List<RegexMethodMapping> regexMethodMappings = new ArrayList<>();
    private static final Pattern PATH_VARIABLE_PATTERN = Pattern.compile("(\\{(.+)})");

    public ControllersAdapter(List<Class<?>> components) throws NoSuchMethodException {
        registerControllers(components);
    }

    public MethodMapping getMethodMapping(String url, String method) {
        MethodMapping methodMapping = methodMappings.get(url + " " + method);
        if (methodMapping != null)
            return methodMapping;
        return getRegexMethodMapping(url, method);
    }

    private void registerControllers(List<Class<?>> components) throws NoSuchMethodException {
        for (Class<?> clazz : components) {
            RestController restController = clazz.getAnnotation(RestController.class);
            Controller controller = clazz.getAnnotation(Controller.class);
            Component component = clazz.getAnnotation(Component.class);

            if (restController != null && !clazz.isInterface())
                registerController(clazz, clazz, true);

            else if (controller != null && !clazz.isInterface())
                registerController(clazz, clazz, false);

            else if (component != null) {
                Class<?>[] interfaces = clazz.getInterfaces();
                for (Class<?> _interface : interfaces) {
                    RestController interfaceRestAnn = _interface.getAnnotation(RestController.class);
                    if (interfaceRestAnn != null) {
                        registerController(clazz, _interface, true);
                        break;
                    }
                    Controller interfaceControllerAnn = _interface.getAnnotation(Controller.class);
                    if (interfaceControllerAnn != null) {
                        registerController(clazz, _interface, false);
                        break;
                    }
                }
            }
        }
    }

    private void registerController(Class<?> controllerClass, Class<?> annotatedClass, boolean isResponseBody) throws NoSuchMethodException {
        controllers.add(controllerClass);

        RequestMapping requestMapping = annotatedClass.getAnnotation(RequestMapping.class);
        String controllerPath = "";
        if (requestMapping != null)
            controllerPath = requestMapping.value();
        Method[] methods = controllerClass.getDeclaredMethods();
        for (Method method : methods) {
            String methodName = method.getName();
            Class<?>[] parameterTypes = method.getParameterTypes();
            Method annotatedMethod = annotatedClass.getMethod(methodName, parameterTypes);

            ResponseBody responseBody = annotatedMethod.getAnnotation(ResponseBody.class);
            if (responseBody != null || method.getReturnType().equals(ResponseEntity.class))
                isResponseBody = true;
            MethodMapping methodMapping = new MethodMapping(controllerClass, method, isResponseBody);

            String path = getPathFromMethod(annotatedMethod, methodMapping);
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
        return PATH_VARIABLE_PATTERN.matcher(finalPath).replaceAll(matchResult -> {
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
