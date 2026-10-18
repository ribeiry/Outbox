package com.ribeiry.pedido;

import com.ribeiry.domain.Pedido;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Tag(name = "Pedidos", description = "Criação e consulta de pedidos de veículos")
@RestController
@RequestMapping("/pedidos")
public class PedidoController {

    private PedidoService service;
    public PedidoController(PedidoService service){
        this.service = service;
    }

    record CriarPedidoRequest(String cpf, String veiculo, BigDecimal valor) {}

    @Operation(summary = "Criar pedido", description = "Cria um novo pedido e registra o evento PEDIDO_CRIADO no Outbox")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Pedido criado com sucesso"),
        @ApiResponse(responseCode = "400", description = "Dados inválidos")
    })
    @PostMapping(consumes = {MediaType.APPLICATION_JSON_VALUE}, produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Pedido> createOrder(@RequestBody CriarPedidoRequest request){


        Pedido pedido = service.save(request.cpf,request.veiculo,request.valor);

        return  ResponseEntity.status(HttpStatus.CREATED).body(pedido);
    }

    @Operation(summary = "Buscar pedido", description = "Retorna um pedido pelo seu ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pedido encontrado"),
        @ApiResponse(responseCode = "404", description = "Pedido não encontrado")
    })
    @GetMapping(value = "/{id}", produces = {MediaType.APPLICATION_JSON_VALUE} )
    public ResponseEntity<Pedido> findaOrder(@PathVariable UUID id) throws Exception {
        Optional<Pedido> pedido;

        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());

    }
}
