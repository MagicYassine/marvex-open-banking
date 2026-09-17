package com.marvex.yapily;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class YapilyPaymentResponseData {
    @JsonProperty("id") private String id;
    @JsonProperty("status") private String status;
}
