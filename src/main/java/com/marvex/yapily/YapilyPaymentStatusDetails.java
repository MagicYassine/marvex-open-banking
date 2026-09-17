package com.marvex.yapily;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class YapilyPaymentStatusDetails {
    @JsonProperty("status") private String status;
    @JsonProperty("statusReason") private String statusReason;
    @JsonProperty("statusReasonDescription") private String statusReasonDescription;
    @JsonProperty("isoStatus") private YapilyIsoStatus isoStatus;
}
