package com.snail.snail_race.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class T2vEvidenceDto {
    private List<Double> frame_importance;
    private List<T2vSegmentDto> segments;
    private List<String> explanations;
}
