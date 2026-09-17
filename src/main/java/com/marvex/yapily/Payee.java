package com.marvex.yapily;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Payee {
    @JsonProperty("name") private String name;
    @JsonProperty("accountIdentifications") private List<AccountIdentification> accountIdentifications;
}
