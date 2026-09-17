package com.marvex.yapily;

import com.fasterxml.jackson.annotation.JsonProperty;

public class YapilyPaymentStatusResponse {
    @JsonProperty("data") private YapilyPaymentStatusData data;

    public String getStatus() {
        if (data == null || data.getPayments() == null || data.getPayments().isEmpty()) return null;
        return data.getPayments().get(0).getStatus();
    }

    public YapilyPaymentStatusDetails getStatusDetails() {
        if (data == null || data.getPayments() == null || data.getPayments().isEmpty()) return null;
        return data.getPayments().get(0).getStatusDetails();
    }
}
