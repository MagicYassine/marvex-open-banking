package com.marvex.yapily;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class YapilyPaymentStatusPayment {
    @JsonProperty("status") private String status;
    @JsonProperty("statusDetails") private YapilyPaymentStatusDetails statusDetails;
}
