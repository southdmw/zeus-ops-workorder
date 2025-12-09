package com.gdu.zeus.ops.workorder.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class LLmRecheckInputs {
    @JsonProperty("Warning_Type")
    private String warningType;

    @JsonProperty("Image")
    private LLmRecheckImage image;
}