package com.marketplace.user;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    @PutMapping(path = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MyProfileResponse updatePhoto(@AuthenticationPrincipal Jwt jwt, @RequestParam("photo") MultipartFile photo) {
        return myProfileService.updatePhoto(jwt, photo);
    }

    @DeleteMapping("/photo")
    public MyProfileResponse removePhoto(@AuthenticationPrincipal Jwt jwt) {
        return myProfileService.removePhoto(jwt);
    }
}
