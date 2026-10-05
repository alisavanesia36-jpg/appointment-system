package com.example.appointmentsystem.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {


    // 参数校验异常
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidationException(
            MethodArgumentNotValidException e) {


        Map<String, Object> result = new HashMap<>();

        result.put(
                "message",
                "参数不能为空"
        );


        return result;
    }
    // 业务异常
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,Object> handleBusinessException(
            BusinessException e) {


        Map<String,Object> result = new HashMap<>();

        result.put(
                "message",
                e.getMessage()
        );

        return result;
    }

    // ==================== v2.0 新增：日期格式解析异常 ====================
    // available-slots 端点接 date=YYYY-MM-DD，非法格式应返回友好提示
    @ExceptionHandler(DateTimeParseException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleDateTimeParseException(
            DateTimeParseException e) {

        Map<String, Object> result = new HashMap<>();
        result.put("message", "日期格式错误，应为 YYYY-MM-DD");
        return result;
    }

}
