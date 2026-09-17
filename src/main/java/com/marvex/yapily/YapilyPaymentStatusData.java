package com.marvex.yapily;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

@Getter
public class YapilyPaymentStatusData {
    @JsonProperty("payments") private List<YapilyPaymentStatusPayment> payments;
}
