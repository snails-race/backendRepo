package com.snail.snail_race.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class T2vAnalysisResponse {
    private String decision;
    private Double t2v_prob;
    private String model_used;
    private T2vEvidenceDto evidence;
    private T2vXaiVisualizationDto xai_visualization;
}
