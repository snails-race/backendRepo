package com.snail.snail_race.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class T2vXaiVisualizationDto {
    private String method;
    private List<T2vHeatmapDto> heatmaps;
}
