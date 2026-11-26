package com.finpilot.admin;

import com.finpilot.security.AuthenticatedUser;
import com.finpilot.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/ml")
@Tag(name = "Admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final RetrainService retrain;

    public AdminController(RetrainService retrain) {
        this.retrain = retrain;
    }

    @PostMapping("/retrain")
    public RetrainService.RetrainResult retrain() {
        AuthenticatedUser me = CurrentUser.get();
        return retrain.retrain(me.id(), me.email());
    }

    @GetMapping("/status")
    public RetrainService.Status status() {
        return retrain.status();
    }
}
