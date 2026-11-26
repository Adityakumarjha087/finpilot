package com.finpilot.recurring;

import com.finpilot.recurring.RecurringDetector.Charge;
import com.finpilot.recurring.RecurringDetector.Recurring;
import com.finpilot.security.CurrentUser;
import com.finpilot.transaction.TransactionRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recurring")
@Tag(name = "Recurring payments")
public class RecurringController {
    private final TransactionRepository repo;
    private final Clock clock;
    private final ZoneId zone;

    public RecurringController(TransactionRepository repo, Clock clock, ZoneId appZone) {
        this.repo = repo;
        this.clock = clock;
        this.zone = appZone;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Recurring> list() {
        List<Charge> charges = repo.findByUserIdAndPostedAtGreaterThanEqualOrderByPostedAtAsc(CurrentUser.id(),
                        clock.instant().minus(Duration.ofDays(400))).stream()
                .filter(t -> t.getAmount().signum() < 0)
                .map(t -> new Charge(t.getMerchant(), t.getCategory(), t.getPostedAt().atZone(zone).toLocalDate(),
                        t.getAmount()))
                .toList();
        return RecurringDetector.detect(charges, LocalDate.now(clock.withZone(zone)));
    }
}
