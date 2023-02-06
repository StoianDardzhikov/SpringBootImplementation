package org.example;

import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

public class ResponseEntity<T> {
    Gson gson;
    private final int httpStatus;
    private final T body;
    private final Map<String, String> headers;

    public int getHttpStatus() {
        return httpStatus;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public ResponseEntity(int httpStatus, Map<String, String> headers, T body) {
        this.httpStatus = httpStatus;
        this.headers = headers;
        this.body = body;
        this.gson = new Gson();
    }

    public ResponseEntity(T body, int httpStatus) {
        this(httpStatus, new HashMap<>(), body);
    }

    public String getEntityAsJson() {
        return gson.toJson(body);
    }
}