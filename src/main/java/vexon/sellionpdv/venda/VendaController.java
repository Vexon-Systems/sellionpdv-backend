package vexon.sellionpdv.venda;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vexon.sellionpdv.venda.dto.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/vendas")
@RequiredArgsConstructor
public class VendaController {

    private final VendaService service;

    @GetMapping
    public ResponseEntity<List<VendaResponseDTO>> listarVendas() {
        return ResponseEntity.ok(service.listarVendasCaixaAtual());
    }

    @PostMapping
    public ResponseEntity<VendaResponseDTO> registrarVenda(
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @RequestBody @Valid VendaRequestDTO dto
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(service.registrarVenda(dto, idempotencyKey));
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<Void> cancelarVenda(
            @PathVariable Long id,
            @RequestBody @Valid CancelamentoVendaRequestDTO dto
    ) {
        service.cancelarVenda(id, dto);
        return ResponseEntity.ok().build();
    }
}