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

import java.time.LocalDate;
import java.util.List;

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
    public ResponseEntity<LancamentoResponseDTO> criar(@Valid @RequestBody LancamentoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LancamentoResponseDTO> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody LancamentoRequestDTO dto) {

        return ResponseEntity.ok(service.atualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
