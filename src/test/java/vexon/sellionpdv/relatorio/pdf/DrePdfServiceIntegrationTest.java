package vexon.sellionpdv.relatorio.pdf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import vexon.sellionpdv.relatorio.RelatorioService;
import vexon.sellionpdv.relatorio.dto.DreCustosDTO;
import vexon.sellionpdv.relatorio.dto.DreDeducoesDTO;
import vexon.sellionpdv.relatorio.dto.DreDespesasOperacionaisDTO;
import vexon.sellionpdv.relatorio.dto.DreResponseDTO;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Smoke test que monta TemplateEngine real + PdfService real + DrePdfService,
 * mocando apenas o RelatorioService. Garante que o template renderiza para diferentes
 * cenários e o OpenHTMLtoPDF produz um PDF válido.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DrePdfService — integração (template + PDF real)")
class DrePdfServiceIntegrationTest {

    @Mock private RelatorioService relatorioService;
    private DrePdfService service;

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
        service = new DrePdfService(relatorioService, pdfService);
    }

    @Test
    @DisplayName("gera PDF válido para DRE com valores positivos padrão")
    void geraPdfParaDrePositivo() {
        when(relatorioService.gerarDreGerencial(INICIO, FIM)).thenReturn(dreLucrativo());

        byte[] bytes = service.gerarDre(INICIO, FIM);

        assertEquals("%PDF-", new String(bytes, 0, 5, StandardCharsets.US_ASCII));
        assertTrue(bytes.length > 0);
    }

    @Test
    @DisplayName("gera PDF válido para DRE com lucro líquido negativo (custos > receita)")
    void geraPdfParaDreComPrejuizo() {
        when(relatorioService.gerarDreGerencial(INICIO, FIM)).thenReturn(dreComPrejuizo());

        byte[] bytes = service.gerarDre(INICIO, FIM);

        assertEquals("%PDF-", new String(bytes, 0, 5, StandardCharsets.US_ASCII));
        assertTrue(bytes.length > 0);
    }

    @Test
    @DisplayName("gera PDF válido para DRE com receita zero (margens em 0%)")
    void geraPdfParaDreComReceitaZero() {
        when(relatorioService.gerarDreGerencial(INICIO, FIM)).thenReturn(dreReceitaZero());

        byte[] bytes = service.gerarDre(INICIO, FIM);

        assertEquals("%PDF-", new String(bytes, 0, 5, StandardCharsets.US_ASCII));
        assertTrue(bytes.length > 0);
    }

    @Test
    @DisplayName("gera PDF válido para DRE sem despesas operacionais (lista vazia)")
    void geraPdfParaDreSemDespesas() {
        when(relatorioService.gerarDreGerencial(INICIO, FIM)).thenReturn(dreSemDespesas());

        byte[] bytes = service.gerarDre(INICIO, FIM);

        assertEquals("%PDF-", new String(bytes, 0, 5, StandardCharsets.US_ASCII));
        assertTrue(bytes.length > 0);
    }

    // ─── fixtures ───────────────────────────────────────────────────────────────

    private static DreResponseDTO dreLucrativo() {
        return new DreResponseDTO(
                "01/06/2026 a 30/06/2026",
                new BigDecimal("10000.00"),
                new DreDeducoesDTO(new BigDecimal("200.00"), new BigDecimal("150.00")),
                new BigDecimal("9650.00"),
                new DreCustosDTO(new BigDecimal("4000.00")),
                new BigDecimal("5650.00"),
                58.55,
                List.of(
                        new DreDespesasOperacionaisDTO("ALUGUEL", new BigDecimal("1500.00")),
                        new DreDespesasOperacionaisDTO("SALARIOS", new BigDecimal("2000.00")),
                        new DreDespesasOperacionaisDTO("AGUA", new BigDecimal("100.00"))
                ),
                new BigDecimal("3600.00"),
                new BigDecimal("2050.00"),
                21.24
        );
    }

    private static DreResponseDTO dreComPrejuizo() {
        return new DreResponseDTO(
                "01/06/2026 a 30/06/2026",
                new BigDecimal("2000.00"),
                new DreDeducoesDTO(new BigDecimal("100.00"), new BigDecimal("50.00")),
                new BigDecimal("1850.00"),
                new DreCustosDTO(new BigDecimal("2200.00")),
                new BigDecimal("-350.00"),
                -18.92,
                List.of(new DreDespesasOperacionaisDTO("ALUGUEL", new BigDecimal("800.00"))),
                new BigDecimal("800.00"),
                new BigDecimal("-1150.00"),
                -62.16
        );
    }

    private static DreResponseDTO dreReceitaZero() {
        return new DreResponseDTO(
                "01/06/2026 a 30/06/2026",
                BigDecimal.ZERO,
                new DreDeducoesDTO(BigDecimal.ZERO, BigDecimal.ZERO),
                BigDecimal.ZERO,
                new DreCustosDTO(BigDecimal.ZERO),
                BigDecimal.ZERO,
                0.0,
                List.of(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0.0
        );
    }

    private static DreResponseDTO dreSemDespesas() {
        return new DreResponseDTO(
                "01/06/2026 a 30/06/2026",
                new BigDecimal("5000.00"),
                new DreDeducoesDTO(new BigDecimal("100.00"), BigDecimal.ZERO),
                new BigDecimal("4900.00"),
                new DreCustosDTO(new BigDecimal("2000.00")),
                new BigDecimal("2900.00"),
                59.18,
                List.of(),
                BigDecimal.ZERO,
                new BigDecimal("2900.00"),
                59.18
        );
    }
}
