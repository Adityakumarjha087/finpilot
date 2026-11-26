package com.finpilot.anomaly;

import com.finpilot.security.CurrentUser;
import com.finpilot.transaction.TransactionDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/anomalies")
@Tag(name = "Anomalies")
public class AnomalyController {
    private final AnomalyService service;

    public AnomalyController(AnomalyService service) {
        this.service = service;
    }

    @PostMapping("/scan")
    public AnomalyService.ScanResult scan() {
        return service.scan(CurrentUser.get());
    }

    @GetMapping
    public List<TransactionDto> list() {
        return service.flagged(CurrentUser.id());
    }

    @PostMapping("/{transactionId}/dismiss")
    public TransactionDto dismiss(@PathVariable long transactionId) {
        return service.dismiss(CurrentUser.get(), transactionId);
    }
}
