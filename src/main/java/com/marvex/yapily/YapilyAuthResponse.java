package com.marvex.yapily;

import com.fasterxml.jackson.annotation.JsonProperty;

public class YapilyAuthResponse {
    @JsonProperty("data") private YapilyAuthResponseData data;

    public String getId() { return data != null ? data.getId() : null; }
    public String getAuthorisationUrl() { return data != null ? data.getAuthorisationUrl() : null; }
}
