package vexon.sellionpdv.relatorio;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vexon.sellionpdv.common.exception.BusinessException;
import vexon.sellionpdv.relatorio.dto.*;
import vexon.sellionpdv.relatorio.pdf.CaixasPdfService;
import vexon.sellionpdv.relatorio.pdf.DrePdfService;
import vexon.sellionpdv.relatorio.pdf.HistoricoVendasPdfService;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/relatorios")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class RelatorioController {

    private final RelatorioService relatorioService;
    private final DrePdfService drePdfService;
    private final HistoricoVendasPdfService historicoVendasPdfService;
    private final CaixasPdfService caixasPdfService;

    @GetMapping("/vendas")
    public ResponseEntity<Page<RelatorioVendaDTO>> listarVendas(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "dataVenda", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(relatorioService.listarVendas(status, pageable));
    }

    @GetMapping("/vendas/{id}")
    public ResponseEntity<ReciboVendaResponseDTO> obterReciboVenda(@PathVariable Long id) {
        return ResponseEntity.ok(relatorioService.obterRecibo(id));
    }

    @GetMapping("/dre")
    public ResponseEntity<DreResponseDTO> obterDreGerencial(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicial,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFinal) {

        if (dataFinal.isBefore(dataInicial)) {
            throw new IllegalArgumentException("A data final não pode ser anterior à data inicial.");
        }

        return ResponseEntity.ok(relatorioService.gerarDreGerencial(dataInicial, dataFinal));
    }

    @GetMapping("/dre.pdf")
    public ResponseEntity<byte[]> baixarDrePdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicial,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFinal) {

        if (dataFinal.isBefore(dataInicial)) {
            throw new BusinessException("A data final não pode ser anterior à data inicial.");
        }

        byte[] pdf = drePdfService.gerarDre(dataInicial, dataFinal);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"dre-" + dataInicial + "-a-" + dataFinal + ".pdf\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(pdf);
    }

    @GetMapping("/vendas.pdf")
    public ResponseEntity<byte[]> baixarHistoricoVendasPdf(
            @RequestParam(required = false) String status) {

        byte[] pdf = historicoVendasPdfService.gerarHistorico(status);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"historico-vendas.pdf\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(pdf);
    }

    @GetMapping("/caixas.pdf")
    public ResponseEntity<byte[]> baixarCaixasPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicial,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFinal) {

        if (dataFinal.isBefore(dataInicial)) {
            throw new BusinessException("A data final não pode ser anterior à data inicial.");
        }

        byte[] pdf = caixasPdfService.gerarCaixas(dataInicial, dataFinal);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"caixas-" + dataInicial + "-a-" + dataFinal + ".pdf\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(pdf);
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