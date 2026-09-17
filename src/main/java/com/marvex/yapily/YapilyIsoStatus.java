package com.marvex.yapily;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class YapilyIsoStatus {
    @JsonProperty("code") private String code;
    @JsonProperty("name") private String name;
}
