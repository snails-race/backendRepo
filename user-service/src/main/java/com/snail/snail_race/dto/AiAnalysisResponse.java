package com.snail.snail_race.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AiAnalysisResponse {
    private String request_id;
    private String decision;
    private String category;
    private Integer triggered_stage;
    private DeepfakeStageDto stage1;
    private DeepfakeStageDto stage2;
    private DeepfakeEvidenceDto evidence;
    private Integer latency_ms;
}
