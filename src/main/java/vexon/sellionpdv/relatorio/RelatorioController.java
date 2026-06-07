package vexon.sellionpdv.relatorio;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vexon.sellionpdv.relatorio.dto.*;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/relatorios")
@RequiredArgsConstructor
public class RelatorioController {

    private final RelatorioService relatorioService;

    @GetMapping("/vendas")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<RelatorioVendaDTO>> listarVendas(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "dataVenda", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(relatorioService.listarVendas(status, pageable));
    }

    @GetMapping("/vendas/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReciboVendaResponseDTO> obterReciboVenda(@PathVariable Long id) {
        return ResponseEntity.ok(relatorioService.obterRecibo(id));
    }

    @GetMapping("/dre")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DreResponseDTO> obterDreGerencial(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicial,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFinal) {

        if (dataFinal.isBefore(dataInicial)) {
            throw new IllegalArgumentException("A data final não pode ser anterior à data inicial.");
        }

        return ResponseEntity.ok(relatorioService.gerarDreGerencial(dataInicial, dataFinal));
    }

    @GetMapping("/caixas")
    @Operation(summary = "Listar relatórios de caixas passados com paginação")
    public ResponseEntity<PageResponseDTO<RelatorioCaixaDTO>> listarCaixas(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicial,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFinal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);

        PageResponseDTO<RelatorioCaixaDTO> response = relatorioService.buscarRelatorioCaixas(
                dataInicial, dataFinal, pageable);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/comparativo")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Gera um balanço comparativo usando datas customizadas")
    public ResponseEntity<RelatorioComparativoResponseDTO> obterRelatorioComparativo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicial,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFinal) {

        if (dataFinal.isBefore(dataInicial)) {
            throw new IllegalArgumentException("A data final não pode ser anterior à data inicial.");
        }

        return ResponseEntity.ok(relatorioService.gerarRelatorioComparativo(dataInicial, dataFinal));
    }
}