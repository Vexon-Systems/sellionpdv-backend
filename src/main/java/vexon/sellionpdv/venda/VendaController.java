package vexon.sellionpdv.venda;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import vexon.sellionpdv.venda.dto.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/vendas")
@RequiredArgsConstructor
public class VendaController {

    private final VendaService vendaService;

    @GetMapping
    public ResponseEntity<List<VendaResponseDTO>> listarVendas() {
        return ResponseEntity.ok(vendaService.listarVendasCaixaAtual());
    }

    @PostMapping
    public ResponseEntity<VendaResponseDTO> registrarVenda(
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @RequestBody @Valid VendaRequestDTO dto,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(vendaService.registrarVenda(dto, idempotencyKey, userDetails.getUsername()));
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<Void> cancelarVenda(
            @PathVariable Long id,
            @RequestBody @Valid CancelamentoVendaRequestDTO dto
    ) {
        vendaService.cancelarVenda(id, dto);
        return ResponseEntity.ok().build();
    }
}