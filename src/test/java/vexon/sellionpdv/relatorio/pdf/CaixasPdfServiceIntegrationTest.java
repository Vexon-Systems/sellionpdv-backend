package vexon.sellionpdv.relatorio.pdf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import vexon.sellionpdv.relatorio.RelatorioService;
import vexon.sellionpdv.relatorio.dto.PageResponseDTO;
import vexon.sellionpdv.relatorio.dto.RelatorioCaixaDTO;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CaixasPdfService — integração (template + PDF real)")
class CaixasPdfServiceIntegrationTest {

    @Mock private RelatorioService relatorioService;
    private CaixasPdfService service;

    private static final LocalDate INICIO = LocalDate.of(2026, 6, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 6, 30);

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);

        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);

        PdfService pdfService = new PdfService(engine);
        service = new CaixasPdfService(relatorioService, pdfService);
    }

    @Test
    @DisplayName("gera PDF válido com mix de caixas abertos e fechados")
    void geraPdfParaMixAbertoFechado() {
        List<RelatorioCaixaDTO> caixas = List.of(
                caixaFechado(1L, BigDecimal.ZERO),
                caixaAberto(2L),
                caixaFechado(3L, new BigDecimal("12.00"))
        );
        when(relatorioService.buscarRelatorioCaixas(eq(INICIO), eq(FIM), any(Pageable.class)))
                .thenReturn(pagina(caixas));

        byte[] bytes = service.gerarCaixas(INICIO, FIM);

        assertEquals("%PDF-", new String(bytes, 0, 5, StandardCharsets.US_ASCII));
        assertTrue(bytes.length > 0);
    }

    @Test
    @DisplayName("gera PDF válido cobrindo furo positivo, negativo e zero")
    void geraPdfParaFurosVariados() {
        List<RelatorioCaixaDTO> caixas = List.of(
                caixaFechado(1L, new BigDecimal("50.00")),   // sobra
                caixaFechado(2L, new BigDecimal("-20.00")),  // falta
                caixaFechado(3L, BigDecimal.ZERO)            // exato
        );
        when(relatorioService.buscarRelatorioCaixas(eq(INICIO), eq(FIM), any(Pageable.class)))
                .thenReturn(pagina(caixas));

        byte[] bytes = service.gerarCaixas(INICIO, FIM);

        assertEquals("%PDF-", new String(bytes, 0, 5, StandardCharsets.US_ASCII));
        assertTrue(bytes.length > 0);
    }

    @Test
    @DisplayName("gera PDF válido quando não há caixas no período")
    void geraPdfParaListaVazia() {
        when(relatorioService.buscarRelatorioCaixas(eq(INICIO), eq(FIM), any(Pageable.class)))
                .thenReturn(pagina(List.of()));

        byte[] bytes = service.gerarCaixas(INICIO, FIM);

        assertEquals("%PDF-", new String(bytes, 0, 5, StandardCharsets.US_ASCII));
        assertTrue(bytes.length > 0);
    }

    // ─── fixtures ───────────────────────────────────────────────────────────────

    private static RelatorioCaixaDTO caixaFechado(Long id, BigDecimal furo) {
        BigDecimal saldoCalc = new BigDecimal("500.00");
        BigDecimal saldoInf = saldoCalc.add(furo);
        return new RelatorioCaixaDTO(
                id, "FECHADO", "Ana Silva", "Bruno Costa",
                OffsetDateTime.parse("2026-06-15T08:00:00-03:00"),
                OffsetDateTime.parse("2026-06-15T18:00:00-03:00"),
                new BigDecimal("100.00"),
                new BigDecimal("400.00"),
                new BigDecimal("30.00"),
                new BigDecimal("20.00"),
                saldoCalc, saldoInf, furo
        );
    }

    private static RelatorioCaixaDTO caixaAberto(Long id) {
        return new RelatorioCaixaDTO(
                id, "ABERTO", "Ana Silva", null,
                OffsetDateTime.parse("2026-06-30T08:00:00-03:00"),
                null,
                new BigDecimal("100.00"),
                new BigDecimal("250.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("350.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }

    private static PageResponseDTO<RelatorioCaixaDTO> pagina(List<RelatorioCaixaDTO> content) {
        return new PageResponseDTO<>(content, 0, content.size(), content.size(), 1);
    }
}
