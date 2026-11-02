package com.ribeiry.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

    private final SecurityConfig config = new SecurityConfig();

    @Test
    void filterChain_deveRetornarNaoNulo() throws Exception {
        HttpSecurity http = mock(HttpSecurity.class, RETURNS_DEEP_STUBS);
        DefaultSecurityFilterChain mockChain = mock(DefaultSecurityFilterChain.class);

        when(http.authorizeHttpRequests(any())).thenReturn(http);
        when(http.csrf(any())).thenReturn(http);
        when(http.headers(any())).thenReturn(http);
        when(http.build()).thenReturn(mockChain);

        SecurityFilterChain chain = config.filterChain(http);

        assertThat(chain).isNotNull();
    }

    @Test
    void filterChain_deveChamarAuthorizeHttpRequests() throws Exception {
        HttpSecurity http = mock(HttpSecurity.class, RETURNS_DEEP_STUBS);
        DefaultSecurityFilterChain mockChain = mock(DefaultSecurityFilterChain.class);

        when(http.authorizeHttpRequests(any())).thenReturn(http);
        when(http.csrf(any())).thenReturn(http);
        when(http.headers(any())).thenReturn(http);
        when(http.build()).thenReturn(mockChain);

        config.filterChain(http);

        verify(http).authorizeHttpRequests(any());
        verify(http).csrf(any());
        verify(http).headers(any());
    }
}
