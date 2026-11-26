package com.finpilot.assistant;

import com.finpilot.common.ApiException;
import org.springframework.http.HttpStatus;

public class UnsupportedQuestionException extends ApiException {
    public UnsupportedQuestionException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
