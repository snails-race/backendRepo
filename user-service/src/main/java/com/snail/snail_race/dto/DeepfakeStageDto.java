package com.snail.snail_race.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@NoArgsConstructor
public class DeepfakeStageDto {
    private double prob;
    private double threshold;
    private Map<String, Double> models;
    private List<String> models_loaded;
}
