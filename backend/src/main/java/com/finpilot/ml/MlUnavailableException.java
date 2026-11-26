package com.finpilot.ml;

import com.finpilot.common.ApiException;
import org.springframework.http.HttpStatus;

public class MlUnavailableException extends ApiException {
    public MlUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
