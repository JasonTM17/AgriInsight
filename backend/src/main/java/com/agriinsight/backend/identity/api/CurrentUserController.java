package com.agriinsight.backend.identity.api;

import com.agriinsight.backend.identity.application.AgriInsightPrincipal;
import com.agriinsight.backend.shared.api.ApiVersion;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiVersion.PREFIX)
@ConditionalOnProperty(prefix = "agriinsight.identity", name = "enabled", havingValue = "true")
public class CurrentUserController {

    @GetMapping("/me")
    CurrentUserResponse currentUser(@AuthenticationPrincipal AgriInsightPrincipal principal) {
        return CurrentUserResponse.from(principal);
    }
}
