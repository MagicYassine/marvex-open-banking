package com.marvex.yapily;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AccountIdentification {
    @JsonProperty("type") private String type;
    @JsonProperty("identification") private String identification;
}
