package com.snail.snail_race.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class T2vHeatmapDto {
    private Integer frame_idx;
    private Double importance;
    private List<String> focus_area;
    private String heatmap_url;
    private String overlay_url;
}
