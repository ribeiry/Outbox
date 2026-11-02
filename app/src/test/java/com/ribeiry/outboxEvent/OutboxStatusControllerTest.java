package com.ribeiry.outboxEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class OutboxStatusControllerTest {

    @Mock
    private OutboxEventsRepository repository;

    @InjectMocks
    private OutboxStatusController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void status_deveRetornar200() throws Exception {
        when(repository.findByStatus(OutboxEvents.Status.PENDING)).thenReturn(Collections.emptyList());
        when(repository.findByStatus(OutboxEvents.Status.PROCESSED)).thenReturn(Collections.emptyList());
        when(repository.findByStatus(OutboxEvents.Status.FAILED)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/outbox/status"))
                .andExpect(status().isOk());
    }

    @Test
    void status_deveRetornarContagensZeroQuandoNaoHaEventos() throws Exception {
        when(repository.findByStatus(OutboxEvents.Status.PENDING)).thenReturn(Collections.emptyList());
        when(repository.findByStatus(OutboxEvents.Status.PROCESSED)).thenReturn(Collections.emptyList());
        when(repository.findByStatus(OutboxEvents.Status.FAILED)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/outbox/status"))
                .andExpect(jsonPath("$.pending").value(0))
                .andExpect(jsonPath("$.processed").value(0))
                .andExpect(jsonPath("$.failed").value(0));
    }

    @Test
    void status_deveRetornarContagensCorretas() throws Exception {
        when(repository.findByStatus(OutboxEvents.Status.PENDING))
                .thenReturn(List.of(new OutboxEvents(), new OutboxEvents()));
        when(repository.findByStatus(OutboxEvents.Status.PROCESSED))
                .thenReturn(List.of(new OutboxEvents()));
        when(repository.findByStatus(OutboxEvents.Status.FAILED))
                .thenReturn(List.of(new OutboxEvents(), new OutboxEvents(), new OutboxEvents()));

        mockMvc.perform(get("/outbox/status"))
                .andExpect(jsonPath("$.pending").value(2))
                .andExpect(jsonPath("$.processed").value(1))
                .andExpect(jsonPath("$.failed").value(3));
    }
}
