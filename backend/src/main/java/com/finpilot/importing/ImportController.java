package com.finpilot.importing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finpilot.common.ApiException;
import com.finpilot.importing.ImportDtos.ImportBatchDto;
import com.finpilot.importing.ImportDtos.ImportResult;
import com.finpilot.importing.ImportDtos.PreviewResponse;
import com.finpilot.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/imports")
@Tag(name = "Imports")
public class ImportController {
    private final ImportService service;
    private final ObjectMapper json;
    private final Validator validator;

    public ImportController(ImportService service, ObjectMapper json, Validator validator) {
        this.service = service;
        this.json = json;
        this.validator = validator;
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PreviewResponse preview(@RequestPart("file") MultipartFile file) {
        return service.preview(file);
    }

    /** {@code mapping} is accepted as a JSON string part so plain FormData uploads work from the browser. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importFile(@RequestPart("file") MultipartFile file, @RequestPart("mapping") String mapping) {
        ColumnMapping parsed;
        try {
            parsed = json.readValue(mapping, ColumnMapping.class);
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("mapping must be valid JSON");
        }
        Set<ConstraintViolation<ColumnMapping>> violations = validator.validate(parsed);
        if (!violations.isEmpty()) {
            var v = violations.iterator().next();
            throw ApiException.badRequest("mapping." + v.getPropertyPath() + " " + v.getMessage());
        }
        return service.importFile(CurrentUser.get(), file, parsed);
    }

    @GetMapping
    public List<ImportBatchDto> history() {
        return service.history(CurrentUser.id());
    }
}
