package com.backendsystemdesignlab.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateShortUrlRequest(

        @NotBlank(message = "URL은 비어 있을 수 없습니다.")
        @Size(max = 2048, message = "URL은 2048자를 넘을 수 없습니다.")
        String longUrl

) {
}
