package vexon.sellionpdv.relatorio.pdf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import vexon.sellionpdv.relatorio.RelatorioService;
import vexon.sellionpdv.relatorio.dto.RelatorioVendaDTO;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("HistoricoVendasPdfService — integração (template + PDF real)")
class HistoricoVendasPdfServiceIntegrationTest {

    @Mock private RelatorioService relatorioService;
    private HistoricoVendasPdfService service;

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
        service = new HistoricoVendasPdfService(relatorioService, pdfService);
    }

    @Test
    @DisplayName("gera PDF válido com mix de vendas concluídas e canceladas")
    void geraPdfParaVendasVariadas() {
        List<RelatorioVendaDTO> vendas = List.of(
                venda(1L, new BigDecimal("42.50"), "PIX", "CONCLUIDA"),
                venda(2L, new BigDecimal("120.00"), "CREDITO", "CONCLUIDA"),
                venda(3L, new BigDecimal("18.00"), "DINHEIRO", "CANCELADA"),
                venda(4L, new BigDecimal("75.00"), "DEBITO", "CONCLUIDA")
        );
        when(relatorioService.listarVendas(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(vendas));

        byte[] bytes = service.gerarHistorico(null);

        assertEquals("%PDF-", new String(bytes, 0, 5, StandardCharsets.US_ASCII));
        assertTrue(bytes.length > 0);
    }

    @Test
    @DisplayName("gera PDF válido quando não há nenhuma venda no período")
    void geraPdfParaListaVazia() {
        when(relatorioService.listarVendas(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        byte[] bytes = service.gerarHistorico(null);

        assertEquals("%PDF-", new String(bytes, 0, 5, StandardCharsets.US_ASCII));
        assertTrue(bytes.length > 0);
    }

    @Test
    @DisplayName("gera PDF válido filtrando só canceladas")
    void geraPdfParaFiltroCanceladas() {
        List<RelatorioVendaDTO> vendas = List.of(
                venda(1L, new BigDecimal("10.00"), "PIX", "CANCELADA"),
                venda(2L, new BigDecimal("55.00"), "DINHEIRO", "CANCELADA")
        );
        when(relatorioService.listarVendas(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(vendas));

        byte[] bytes = service.gerarHistorico("CANCELADA");

        assertEquals("%PDF-", new String(bytes, 0, 5, StandardCharsets.US_ASCII));
        assertTrue(bytes.length > 0);
    }

    private static RelatorioVendaDTO venda(Long id, BigDecimal valor, String forma, String status) {
        return new RelatorioVendaDTO(
                id,
                Instant.parse("2026-06-30T17:30:00Z"),
                valor,
                forma,
                status,
                "Maria Operadora"
        );
    }
}
