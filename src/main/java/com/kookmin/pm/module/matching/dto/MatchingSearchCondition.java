package com.kookmin.pm.module.matching.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
public class MatchingSearchCondition {
    private String title;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Double distance;
    private String status;
    private Integer maxCount;
    private Long host;
    private Double latitude;
    private Double longitude;
    private String category;
}
