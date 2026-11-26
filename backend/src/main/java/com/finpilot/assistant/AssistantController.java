package com.finpilot.assistant;

import com.finpilot.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assistant")
@Tag(name = "Assistant (text-to-SQL)")
public class AssistantController {
    private final AssistantService service;

    public AssistantController(AssistantService service) {
        this.service = service;
    }

    public record QueryRequest(@NotBlank @Size(max = 300) String question) {}

    @PostMapping("/query")
    @Operation(summary = "Answer a natural-language question with validated, read-only SQL")
    public AssistantService.AnswerDto query(@Valid @RequestBody QueryRequest req) {
        return service.ask(CurrentUser.get(), req.question());
    }

    @GetMapping("/examples")
    public List<String> examples() {
        return AssistantService.EXAMPLES;
    }

    @GetMapping("/history")
    public List<AssistantService.HistoryDto> history() {
        return service.history(CurrentUser.id());
    }
}
