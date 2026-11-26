package com.finpilot.transaction;

import com.finpilot.common.PageResponse;
import com.finpilot.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Transactions")
@Validated
public class TransactionController {
    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    public record UpdateRequest(@Size(max = 40) String category, @Size(max = 1000) String notes) {}

    @GetMapping("/categories")
    public List<String> categories() {
        return Categories.ALL;
    }

    @GetMapping("/transactions")
    public PageResponse<TransactionDto> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(200) int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) @Size(max = 40) String category,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Boolean flagged) {
        return service.list(CurrentUser.id(), new TransactionService.Filter(category, q, from, to, flagged), page,
                size, sort);
    }

    @PatchMapping("/transactions/{id}")
    public TransactionDto update(@PathVariable long id, @Valid @RequestBody UpdateRequest req) {
        return service.update(CurrentUser.get(), id, req.category(), req.notes());
    }

    @DeleteMapping("/transactions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        service.delete(CurrentUser.get(), id);
    }
}
