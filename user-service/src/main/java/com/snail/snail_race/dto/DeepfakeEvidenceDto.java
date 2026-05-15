package com.snail.snail_race.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@NoArgsConstructor
public class DeepfakeEvidenceDto {
    private Integer suspect_frame_idx;
    private String face_image_base64;
    private List<Integer> face_bbox_in_frame_xyxy;
    private Double detect_conf;
    private Double blur_var;
    private Integer n_frames_analyzed;
    private Map<String, String> heatmaps;
    private DeepfakeSeAttentionDto se_attention;
    private Map<String, List<DeepfakeRegionDto>> regions;
}
