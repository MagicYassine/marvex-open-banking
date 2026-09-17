package com.marvex.yapily;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class YapilyPaymentResponse {
    @JsonProperty("data") private YapilyPaymentResponseData data;

    public String getId() { return getData() != null ? getData().getId() : null; }
    public String getStatus() { return getData() != null ? getData().getStatus() : null; }
}
