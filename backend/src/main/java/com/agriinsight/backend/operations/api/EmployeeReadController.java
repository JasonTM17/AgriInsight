package com.agriinsight.backend.operations.api;

import com.agriinsight.backend.operations.application.EmployeeQuery;
import com.agriinsight.backend.operations.application.EmployeeService;
import com.agriinsight.backend.shared.api.ApiVersion;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiVersion.PREFIX + "/employees")
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class EmployeeReadController {

    private final EmployeeService employees;

    public EmployeeReadController(EmployeeService employees) {
        this.employees = employees;
    }

    @GetMapping
    ResponseEntity<EmployeePageResponse> list(
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int offset,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search) {
        var page = employees.list(new EmployeeQuery(
                limit, offset, Optional.ofNullable(active), Optional.ofNullable(search)));
        return ResponseEntity.ok(EmployeePageResponse.from(page));
    }

    @GetMapping("/eligible")
    ResponseEntity<EmployeeEligiblePageResponse> eligible(
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int offset,
            @RequestParam(required = false) String search) {
        var page = employees.eligible(new EmployeeQuery(
                limit, offset, Optional.of(true), Optional.ofNullable(search)));
        return ResponseEntity.ok(EmployeeEligiblePageResponse.from(page));
    }

    @GetMapping("/{id}")
    ResponseEntity<EmployeeResponse> get(@PathVariable("id") UUID employeeId) {
        EmployeeResponse response = EmployeeResponse.from(employees.get(employeeId));
        return ResponseEntity.ok().eTag("\"" + response.version() + "\"").body(response);
    }
}
