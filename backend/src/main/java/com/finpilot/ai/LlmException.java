package com.finpilot.ai;

import com.finpilot.common.ApiException;
import org.springframework.http.HttpStatus;

public class LlmException extends ApiException {
    public LlmException(String message) {
        super(HttpStatus.BAD_GATEWAY, message);
    }
}
