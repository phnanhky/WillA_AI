package com.willa.ai.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ExtractLayersRequest {
    @NotBlank(message = "image_base64 is required")
    @JsonProperty("image_base64")
    private String imageBase64;

    @JsonProperty("mime_type")
    private String mimeType = "image/png";

    @Min(1)
    @Max(10)
    @JsonProperty("num_layers")
    private Integer numLayers = 5;
}
