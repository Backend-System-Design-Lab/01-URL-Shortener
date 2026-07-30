package com.backendsystemdesignlab.urlshortener.controller;

import com.backendsystemdesignlab.urlshortener.service.ShortUrlService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShortUrlController.class)
class ShortUrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShortUrlService shortUrlService;

    @Test
    @DisplayName("원본 URL을 전달하면 단축 URL을 생성한다")
    void createShortUrl() throws Exception {
        given(shortUrlService.createShortUrl("https://www.google.com"))
                .willReturn("2TX");

        mockMvc.perform(post("/api/v1/data/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "longUrl": "https://www.google.com"
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("2TX"))
                .andExpect(jsonPath("$.shortUrl")
                        .value("http://localhost/2TX"));
    }

    @Test
    @DisplayName("원본 URL이 비어 있으면 400을 반환한다")
    void createShortUrlWithBlankUrl() throws Exception {
        mockMvc.perform(post("/api/v1/data/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "longUrl": ""
                        }
                        """))
                .andExpect(status().isBadRequest());
    }
}