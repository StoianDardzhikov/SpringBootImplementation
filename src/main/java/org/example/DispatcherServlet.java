package org.example;

import com.google.gson.Gson;
import org.example.Adapters.ControllersAdapter;
import org.example.AnnotationApplicationContext;
import org.example.MethodMapping;
import org.example.ResponseEntity;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;

public class DispatcherServlet extends HttpServlet {
    private final ControllersAdapter controllersAdapter;
    private final AnnotationApplicationContext applicationContext;
    private final Gson gson;

    public DispatcherServlet(ControllersAdapter controllersAdapter, AnnotationApplicationContext applicationContext) {
        this.controllersAdapter = controllersAdapter;
        this.applicationContext = applicationContext;
        this.gson = new Gson();
    }

    private void handleMethod(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        MethodMapping methodMapping = (MethodMapping) req.getAttribute("method");
        Enumeration<String> paramNames = req.getParameterNames();
        for (String paramName; paramNames.hasMoreElements();) {
            paramName = paramNames.nextElement();
            String value = req.getParameter(paramName);
            methodMapping.addRequestParam(paramName, value);
        }

        if (req.getMethod().equals("POST") || req.getMethod().equals("PUT")) {
            BufferedReader bufferedReader = req.getReader();
            Object o = gson.fromJson(bufferedReader, methodMapping.getRequestBodyType());
            methodMapping.setRequestBody(o);
        }

        Class<?> controllerClass = methodMapping.controller;
        try {
            Object instance = applicationContext.getInstance(controllerClass);
            Object responseObj = methodMapping.invoke(instance);
            if (!methodMapping.isResponseBody) {
                resp.setStatus(HttpServletResponse.SC_OK);
                return;
            }

            ResponseEntity<?> responseEntity = extractResponseEntity(responseObj);
            resp.setStatus(responseEntity.getHttpStatus());

            Map<String, String> headers = responseEntity.getHeaders();
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                resp.setHeader(entry.getKey(), entry.getValue());
            }

            resp.getWriter().write(responseEntity.getEntityAsJson());
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    private ResponseEntity<?> extractResponseEntity(Object result) {
        if (result.getClass().equals(ResponseEntity.class))
            return (ResponseEntity<?>) result;

        return new ResponseEntity<>(result, HttpServletResponse.SC_OK);
    }

    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String method = req.getMethod();
        MethodMapping controllerMapping = controllersAdapter.getMethodMapping(req.getPathInfo(), method);
        System.out.println(controllerMapping);
        System.out.println(controllersAdapter.methodMappings);
        System.out.println(controllersAdapter.regexMethodMappings);
        if (controllerMapping == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        req.setAttribute("method", controllerMapping);
        handleMethod(req, resp);
    }
}