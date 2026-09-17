package com.marvex.yapily;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class YapilyAuthResponseData {
    @JsonProperty("id") private String id;
    @JsonProperty("authorisationUrl") private String authorisationUrl;
}
