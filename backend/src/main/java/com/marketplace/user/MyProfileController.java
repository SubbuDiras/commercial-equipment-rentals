package com.marketplace.user;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Any signed-in user's own profile (renter, owner or admin). */
@RestController
@RequestMapping("/api/v1/me")
public class MyProfileController {

    private final MyProfileService myProfileService;

    public MyProfileController(MyProfileService myProfileService) {
        this.myProfileService = myProfileService;
    }

    @GetMapping
    public MyProfileResponse get(@AuthenticationPrincipal Jwt jwt) {
        return myProfileService.get(jwt);
    }

    @PutMapping
    public MyProfileResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MyProfileUpdateRequest request) {
        return myProfileService.update(jwt, request);
    }
}
