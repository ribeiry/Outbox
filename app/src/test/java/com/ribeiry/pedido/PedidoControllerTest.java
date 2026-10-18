package com.ribeiry.pedido;

import com.ribeiry.domain.Pedido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PedidoControllerTest {

    @Mock
    private PedidoService pedidoService;

    @InjectMocks
    private PedidoController pedidoController;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(pedidoController).build();
    }

    @Test
    void criarPedido_deveRetornar201() throws Exception {
        Pedido pedido = pedidoCriado();
        when(pedidoService.save(any(), any(), any())).thenReturn(pedido);

        mockMvc.perform(post("/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpf\":\"123.456.789-00\",\"veiculo\":\"Honda Civic\",\"valor\":120000.00}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clienteCpf").value("123.456.789-00"))
                .andExpect(jsonPath("$.veiculo").value("Honda Civic"))
                .andExpect(jsonPath("$.status").value("CRIADO"));
    }

    @Test
    void buscarPedido_deveRetornar200QuandoEncontrado() throws Exception {
        Pedido pedido = pedidoCriado();
        when(pedidoService.findById(pedido.getId())).thenReturn(Optional.of(pedido));

        mockMvc.perform(get("/pedidos/{id}", pedido.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(pedido.getId().toString()));
    }

    @Test
    void buscarPedido_deveRetornar404QuandoNaoEncontrado() throws Exception {
        UUID id = UUID.randomUUID();
        when(pedidoService.findById(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/pedidos/{id}", id))
                .andExpect(status().isNotFound());
    }

    private Pedido pedidoCriado() {
        Pedido pedido = new Pedido();
        pedido.setId(UUID.randomUUID());
        pedido.setClienteCpf("123.456.789-00");
        pedido.setVeiculo("Honda Civic");
        pedido.setValor(BigDecimal.valueOf(120000));
        pedido.setStatus(Pedido.Status.CRIADO);
        return pedido;
    }
}
