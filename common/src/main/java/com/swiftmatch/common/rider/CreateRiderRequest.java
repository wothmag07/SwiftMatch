package com.swiftmatch.common.rider;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /v1/riders}. Both fields optional: an
 * anonymous rider gets a server-generated name.
 */
public record CreateRiderRequest(
        @Size(min = 1, max = 120) String name,
        @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "phone must be E.164 format if present")
        String phone
) {
}
