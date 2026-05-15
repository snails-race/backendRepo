package com.snail.snail_race.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class AiAnalysisResponse {
    private String request_id;
    private String decision;
    private Double score;
    private Double threshold;
    private List<DeepfakeModelDto> models;
    private DeepfakeEvidenceDto evidence;
    private Integer latency_ms;
}
