package vexon.sellionpdv.relatorio.pdf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.thymeleaf.context.Context;
import vexon.sellionpdv.relatorio.RelatorioService;
import vexon.sellionpdv.relatorio.dto.PageResponseDTO;
import vexon.sellionpdv.relatorio.dto.RelatorioCaixaDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CaixasPdfService")
class CaixasPdfServiceTest {

    @Mock private RelatorioService relatorioService;
    @Mock private PdfService pdfService;
    @InjectMocks private CaixasPdfService service;

    private static final LocalDate INICIO = LocalDate.of(2026, 6, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 6, 30);

    @Nested
    @DisplayName("gerarCaixas")
    class GerarCaixas {

        @Test
        @DisplayName("delega ao RelatorioService com Pageable.unpaged e chama PdfService com template correto")
        void delegaComPageableUnpaged() {
            when(relatorioService.buscarRelatorioCaixas(eq(INICIO), eq(FIM), any(Pageable.class)))
                    .thenReturn(pagina(List.of(caixaFechado(10L, BigDecimal.ZERO))));
            when(pdfService.gerarPdf(eq("pdf/caixas"), any(Context.class)))
                    .thenReturn(new byte[]{9, 9, 9});

            byte[] resultado = service.gerarCaixas(INICIO, FIM);

            assertArrayEquals(new byte[]{9, 9, 9}, resultado);
            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(relatorioService).buscarRelatorioCaixas(eq(INICIO), eq(FIM), pageableCaptor.capture());
            assertTrue(pageableCaptor.getValue().isUnpaged(), "Pageable deve ser unpaged");
        }

        @Test
        @DisplayName("marca furo positivo e negativo nas linhas corretamente")
        void marcaFuroPositivoNegativoENulo() {
            List<RelatorioCaixaDTO> caixas = List.of(
                    caixaFechado(1L, new BigDecimal("15.00")),      // furo positivo (sobra)
                    caixaFechado(2L, new BigDecimal("-8.00")),      // furo negativo (falta)
                    caixaFechado(3L, BigDecimal.ZERO)               // sem furo
            );
            when(relatorioService.buscarRelatorioCaixas(eq(INICIO), eq(FIM), any(Pageable.class)))
                    .thenReturn(pagina(caixas));

            service.gerarCaixas(INICIO, FIM);

            ArgumentCaptor<Context> ctxCaptor = ArgumentCaptor.forClass(Context.class);
            verify(pdfService).gerarPdf(eq("pdf/caixas"), ctxCaptor.capture());
            CaixasView view = (CaixasView) ctxCaptor.getValue().getVariable("caixas");

            assertEquals(3, view.getQtdeCaixas());
            assertTrue(view.getLinhas().get(0).isFuroPositivo());
            assertFalse(view.getLinhas().get(0).isFuroNegativo());
            assertTrue(view.getLinhas().get(1).isFuroNegativo());
            assertFalse(view.getLinhas().get(2).isFuroPositivo());
            assertFalse(view.getLinhas().get(2).isFuroNegativo());
        }

        @Test
        @DisplayName("período do ViewModel é formatado com as datas informadas")
        void periodoFormatadoDdMmYyyy() {
            when(relatorioService.buscarRelatorioCaixas(any(), any(), any(Pageable.class)))
                    .thenReturn(pagina(List.of()));

            service.gerarCaixas(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

            ArgumentCaptor<Context> ctxCaptor = ArgumentCaptor.forClass(Context.class);
            verify(pdfService).gerarPdf(eq("pdf/caixas"), ctxCaptor.capture());
            CaixasView view = (CaixasView) ctxCaptor.getValue().getVariable("caixas");

            assertEquals("01/06/2026 a 30/06/2026", view.getPeriodo());
            assertTrue(view.isSemDados());
        }
    }

    // ─── fixtures ───────────────────────────────────────────────────────────────

    private static RelatorioCaixaDTO caixaFechado(Long id, BigDecimal furo) {
        BigDecimal saldoCalc = new BigDecimal("500.00");
        BigDecimal saldoInf = saldoCalc.add(furo);
        return new RelatorioCaixaDTO(
                id, "FECHADO", "Op. Abertura", "Op. Fechamento",
                OffsetDateTime.parse("2026-06-15T08:00:00-03:00"),
                OffsetDateTime.parse("2026-06-15T18:00:00-03:00"),
                new BigDecimal("100.00"),
                new BigDecimal("400.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                saldoCalc,
                saldoInf,
                furo
        );
    }

    private static PageResponseDTO<RelatorioCaixaDTO> pagina(List<RelatorioCaixaDTO> content) {
        return new PageResponseDTO<>(content, 0, content.size(), content.size(), 1);
    }
}
