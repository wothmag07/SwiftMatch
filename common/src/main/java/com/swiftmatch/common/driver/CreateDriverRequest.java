package com.swiftmatch.common.driver;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateDriverRequest(
        @NotBlank @Size(min = 1, max = 120) String name,
        @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "phone must be E.164 format if present")
        String phone,
        @NotBlank @Size(min = 1, max = 60) String vehicle
) {
}
