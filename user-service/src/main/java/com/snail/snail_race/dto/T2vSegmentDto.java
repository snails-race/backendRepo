package com.snail.snail_race.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class T2vSegmentDto {
    private Integer start_frame;
    private Integer end_frame;
    private String type;
    private Double confidence;
}
