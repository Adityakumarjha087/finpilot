package com.finpilot.forecast;

import com.finpilot.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/forecast")
@Tag(name = "Forecast")
@Validated
public class ForecastController {
    private final ForecastService service;

    public ForecastController(ForecastService service) {
        this.service = service;
    }

    @GetMapping
    public ForecastService.ForecastResult forecast(@RequestParam(defaultValue = "3") @Min(1) @Max(6) int horizon) {
        return service.forecast(CurrentUser.id(), horizon);
    }
}
