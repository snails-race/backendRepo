package com.snail.snail_race.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class VideoUploadResponse {
    private Long video_id;
    private String status;
}
