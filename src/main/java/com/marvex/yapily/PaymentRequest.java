package com.marvex.yapily;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PaymentRequest {
    @JsonProperty("type") private String type;
    @JsonProperty("paymentIdempotencyId") private String paymentIdempotencyId;
    @JsonProperty("reference") private String reference;
    @JsonProperty("contextType") private String contextType;
    @JsonProperty("amount") private Amount amount;
    @JsonProperty("payee") private Payee payee;
}
