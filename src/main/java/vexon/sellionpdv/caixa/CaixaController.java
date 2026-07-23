package vexon.sellionpdv.caixa;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vexon.sellionpdv.caixa.dto.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/caixa")
@RequiredArgsConstructor
public class CaixaController {

    private final CaixaService service;

    @GetMapping("/operacional")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<CaixaOperacionalResponseDTO> buscarVisaoOperacional() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.buscarVisaoOperacional());
    }

    @GetMapping("/atual")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CaixaResponseDTO> buscarCaixaAtual() {
        Caixa caixa = service.buscarCaixaAtual();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new CaixaResponseDTO(caixa));
    }

    @GetMapping("/movimentacao")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<MovimentacaoCaixaResponseDTO>> listarMovimentacoes() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.listarMovimentacoesCaixaAtual());
    }

    @PostMapping("/abrir")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<CaixaResponseDTO> abrirCaixa(@RequestBody @Valid CaixaRequestDTO dto) {
        Caixa caixa = service.abrirCaixa(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(new CaixaResponseDTO(caixa));
    }

    @PostMapping("/movimentacao")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<Void> registrarMovimentacao(
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @RequestBody @Valid MovimentacaoCaixaRequestDTO dto) {
        service.registrarMovimentacao(dto, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/fechar")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<CaixaFechamentoResponseDTO> fecharCaixa(@RequestBody @Valid CaixaFechamentoRequestDTO dto) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.fecharCaixa(dto));
    }
}
