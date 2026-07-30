package com.backendsystemdesignlab.urlshortener.controller;

import com.backendsystemdesignlab.urlshortener.service.ShortUrlService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RedirectController.class)
class RedirectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShortUrlService shortUrlService;

    @Test
    @DisplayName("단축 URL 요청 시 원본 URL로 302 리다이렉트한다")
    void redirect() throws Exception {
        given(shortUrlService.getLongUrl("2TX"))
                .willReturn("https://www.google.com");

        mockMvc.perform(get("/api/v1/2TX"))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        "https://www.google.com"
                ));
    }

    @Test
    @DisplayName("존재하지 않는 단축 URL은 404를 반환한다")
    void redirectNotFound() throws Exception {
        given(shortUrlService.getLongUrl("missing"))
                .willThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "단축 URL을 찾을 수 없습니다."
                ));

        mockMvc.perform(get("/api/v1/missing"))
                .andExpect(status().isNotFound());
    }

}