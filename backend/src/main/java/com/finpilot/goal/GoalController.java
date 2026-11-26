package com.finpilot.goal;

import com.finpilot.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/goals")
@Tag(name = "Savings goals")
public class GoalController {
    private final GoalService service;

    public GoalController(GoalService service) {
        this.service = service;
    }

    public record GoalRequest(@NotBlank @Size(max = 120) String name,
                              @NotNull @DecimalMin("1.00") @DecimalMax("1000000000") BigDecimal targetAmount,
                              @NotNull @DecimalMin("0.00") BigDecimal savedAmount,
                              @NotNull @Future LocalDate targetDate) {}

    @GetMapping
    public List<GoalService.GoalDto> list() {
        return service.list(CurrentUser.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalService.GoalDto create(@Valid @RequestBody GoalRequest r) {
        return service.create(CurrentUser.get(), r.name(), r.targetAmount(), r.savedAmount(), r.targetDate());
    }

    @PutMapping("/{id}")
    public GoalService.GoalDto update(@PathVariable long id, @Valid @RequestBody GoalRequest r) {
        return service.update(CurrentUser.get(), id, r.name(), r.targetAmount(), r.savedAmount(), r.targetDate());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(CurrentUser.get(), id);
    }

    @GetMapping("/{id}/suggestion")
    public GoalService.Suggestion suggestion(@PathVariable long id) {
        return service.suggest(CurrentUser.get(), id);
    }
}
