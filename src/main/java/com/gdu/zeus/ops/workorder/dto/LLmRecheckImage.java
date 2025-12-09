package com.gdu.zeus.ops.workorder.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class LLmRecheckImage {
    @JsonProperty("transfer_method")
    private String transferMethod;

    @JsonProperty("url")
    private String url;

    @JsonProperty("type")
    private String type;

}
