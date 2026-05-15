package com.snail.snail_race.controller;

import com.snail.snail_race.dto.VideoResultResponse;
import com.snail.snail_race.dto.VideoUploadResponse;
import com.snail.snail_race.dto.VideoUrlRequest;
import com.snail.snail_race.service.ResultService;
import com.snail.snail_race.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;
    private final ResultService resultService;

    @PostMapping("/upload")
    public ResponseEntity<VideoUploadResponse> uploadVideo(
            @RequestParam("type") String type,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = null;

        if (jwt != null && jwt.getClaim("userId") != null) {
            userId = ((Number) jwt.getClaim("userId")).longValue();
        }

        VideoUploadResponse response = videoService.uploadVideo(userId, type, file);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/url")
    public ResponseEntity<VideoUploadResponse> submitVideoUrl(
            @RequestBody VideoUrlRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = null;

        if (jwt != null && jwt.getClaim("userId") != null) {
            userId = ((Number) jwt.getClaim("userId")).longValue();
        }

        VideoUploadResponse response = videoService.saveVideoUrl(userId, request.getUrl(), request.getType());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<VideoUploadResponse> getVideoStatus(@PathVariable Long id) {
        return ResponseEntity.ok(videoService.getVideoStatus(id));
    }

    @GetMapping("/{id}/result")
    public ResponseEntity<VideoResultResponse> getVideoResult(@PathVariable Long id) {
        return ResponseEntity.ok(resultService.getVideoResult(id));
    }
}
