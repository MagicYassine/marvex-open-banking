package com.marvex.yapily;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PaymentAuthRequest {
    @JsonProperty("userUuid") private String userUuid;
    @JsonProperty("institutionId") private String institutionId;
    @JsonProperty("callback") private String callback;
    @JsonProperty("paymentRequest") private PaymentRequest paymentRequest;
}
