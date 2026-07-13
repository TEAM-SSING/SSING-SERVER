package org.sopt.ssingserver.global.swagger.success;

import java.util.LinkedHashMap;
import java.util.Map;
import org.sopt.ssingserver.global.response.SuccessCode;

public record ApiSuccessExampleValue(
        String name,
        String summary,
        Object value
) {

    public static ApiSuccessExampleValue success(
            String name,
            String summary,
            SuccessCode successCode,
            Object data
    ) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("success", true);
        value.put("code", successCode.getCode());
        value.put("message", successCode.getMessage());
        value.put("data", data);
        return new ApiSuccessExampleValue(name, summary, value);
    }
}
