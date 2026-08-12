package vexon.sellionpdv.financeiro;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vexon.sellionpdv.financeiro.dto.LancamentoRequestDTO;
import vexon.sellionpdv.financeiro.dto.LancamentoResponseDTO;
import vexon.sellionpdv.financeiro.dto.LancamentoCriacaoResultado;
import vexon.sellionpdv.financeiro.dto.CancelamentoLancamentoRequestDTO;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/financeiro/lancamentos")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class LancamentoFinanceiroController {

    private final LancamentoFinanceiroService service;

    @GetMapping
    public ResponseEntity<List<LancamentoResponseDTO>> listar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicial,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFinal) {

        return ResponseEntity.ok(service.listarPorPeriodo(dataInicial, dataFinal));
    }

    @PostMapping
    public ResponseEntity<LancamentoResponseDTO> criar(
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody LancamentoRequestDTO dto) {
        LancamentoCriacaoResultado resultado = service.criar(dto, idempotencyKey);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(
                resultado.replayed() ? HttpStatus.OK : HttpStatus.CREATED);
        if (resultado.replayed()) {
            response.header("Idempotent-Replayed", "true");
        }
        return response.body(resultado.lancamento());
    }

    @PutMapping("/{id}")
    public ResponseEntity<LancamentoResponseDTO> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody LancamentoRequestDTO dto) {

        return ResponseEntity.ok(service.atualizar(id, dto));
    }

    @PostMapping("/{id}/cancelamento")
    public ResponseEntity<LancamentoResponseDTO> cancelar(
            @PathVariable Long id,
            @Valid @RequestBody CancelamentoLancamentoRequestDTO dto) {
        return ResponseEntity.ok(service.cancelar(id, dto));
    }
}
