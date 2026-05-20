package vexon.sellionpdv.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vexon.sellionpdv.dashboard.dto.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService service;

    @GetMapping("/kpis")
    public ResponseEntity<KpiResponseDTO> getKpis(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicial,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFinal) {
        return ResponseEntity.ok(service.obterKpis(dataInicial, dataFinal));
    }

    @GetMapping("/pagamentos")
    public ResponseEntity<List<PagamentoDashboardDTO>> getPagamentos(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicial,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFinal) {
        return ResponseEntity.ok(service.obterPagamentos(dataInicial, dataFinal));
    }

    @GetMapping("/produtos/top")
    public ResponseEntity<List<ProdutoTopResponseDTO>> getProdutosTop(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicial,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFinal) {
        return ResponseEntity.ok(service.obterProdutosTop(dataInicial, dataFinal));
    }

    @GetMapping("/categorias")
    public ResponseEntity<List<CategoriaDashboardResponseDTO>> getCategorias(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicial,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFinal) {
        return ResponseEntity.ok(service.obterCategoriasDashboard(dataInicial, dataFinal));
    }

    @GetMapping("/faturamento/serie-temporal")
    public ResponseEntity<List<SerieTemporalResponseDTO>> getSerieTemporal(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicial,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFinal) {
        return ResponseEntity.ok(service.obterSerieTemporal(dataInicial, dataFinal));
    }

    @GetMapping("/caixa")
    public ResponseEntity<CaixaDashboardResponseDTO> getDadosCaixa(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicial,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFinal) {
        return ResponseEntity.ok(service.obterDadosCaixa(dataInicial, dataFinal));
    }

}