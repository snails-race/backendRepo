package com.snail.snail_race.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class VideoResultResponse {
    private Long video_id;
    private String final_verdict;
    private Float deepfake_score;
    private Float t2v_score;
    private String xai_text;
    private String xai_heatmap_url;
    private List<FrameProbDto> suspicious_frames;
    private List<FrameProbDto> per_frame_probs;
}
