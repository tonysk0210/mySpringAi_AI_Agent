package com.example.myagentclient.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Mailpit API 回應中的電子郵件地址格式。
 */
public record MailpitAddress(
        @JsonProperty("Name") String name,
        @JsonProperty("Address") String address
) {
}
