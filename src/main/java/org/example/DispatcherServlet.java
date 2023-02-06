package org.example;

import com.google.gson.Gson;
import org.example.DependencyInjector.Container;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;

public class DispatcherServlet extends HttpServlet {
    private final ApplicationContext applicationContext;
    private final Container container;
    private final Gson gson;

    public DispatcherServlet(ApplicationContext applicationContext, Container container) {
        this.applicationContext = applicationContext;
        this.container = container;
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
            Object instance = container.getInstance(controllerClass);
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
        MethodMapping controllerMapping = applicationContext.getMethodMapping(req.getPathInfo(), method);
        if (controllerMapping == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        req.setAttribute("method", controllerMapping);
        handleMethod(req, resp);
    }
}