package com.finpilot.budget;

import com.finpilot.budget.BudgetService.BudgetAlert;
import com.finpilot.budget.BudgetService.BudgetStatus;
import com.finpilot.common.Months;
import com.finpilot.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/budgets")
@Tag(name = "Budgets")
public class BudgetController {
    private final BudgetService service;
    private final Clock clock;
    private final ZoneId zone;

    public BudgetController(BudgetService service, Clock clock, ZoneId appZone) {
        this.service = service;
        this.clock = clock;
        this.zone = appZone;
    }

    public record BudgetRequest(@NotBlank String category,
                                @NotNull @DecimalMin("1.00") @DecimalMax("100000000") BigDecimal monthlyLimit) {}

    private YearMonth month(String m) {
        return Months.parseOrCurrent(m, clock, zone);
    }

    @GetMapping
    public List<BudgetStatus> list(@RequestParam(required = false) String month) {
        return service.statuses(CurrentUser.id(), month(month));
    }

    @GetMapping("/alerts")
    public List<BudgetAlert> alerts(@RequestParam(required = false) String month) {
        return service.alerts(CurrentUser.id(), month(month));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BudgetStatus create(@Valid @RequestBody BudgetRequest req) {
        return service.create(CurrentUser.get(), req.category(), req.monthlyLimit(), month(null));
    }

    @PutMapping("/{id}")
    public BudgetStatus update(@PathVariable long id, @Valid @RequestBody BudgetRequest req) {
        return service.update(CurrentUser.get(), id, req.category(), req.monthlyLimit(), month(null));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(CurrentUser.get(), id);
    }
}
