package vexon.sellionpdv.relatorio.pdf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.thymeleaf.context.Context;
import vexon.sellionpdv.relatorio.RelatorioService;
import vexon.sellionpdv.relatorio.dto.RelatorioVendaDTO;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HistoricoVendasPdfService")
class HistoricoVendasPdfServiceTest {

    @Mock private RelatorioService relatorioService;
    @Mock private PdfService pdfService;
    @InjectMocks private HistoricoVendasPdfService service;

    @Nested
    @DisplayName("gerarHistorico")
    class GerarHistorico {

        @Test
        @DisplayName("delega ao RelatorioService com Pageable.unpaged e chama PdfService com template correto")
        void delegaComPageableUnpaged() {
            Page<RelatorioVendaDTO> pagina = new PageImpl<>(List.of(exemploVenda(1L, "CONCLUIDA")));
            when(relatorioService.listarVendas(eq("CONCLUIDA"), any(Pageable.class))).thenReturn(pagina);
            when(pdfService.gerarPdf(eq("pdf/historico-vendas"), any(Context.class)))
                    .thenReturn(new byte[]{1, 2, 3});

            byte[] resultado = service.gerarHistorico("CONCLUIDA");

            assertArrayEquals(new byte[]{1, 2, 3}, resultado);

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(relatorioService).listarVendas(eq("CONCLUIDA"), pageableCaptor.capture());
            assertTrue(pageableCaptor.getValue().isUnpaged(),
                    "Pageable deve ser unpaged para trazer todas as vendas ao PDF");
        }

        @Test
        @DisplayName("filtro de status é refletido no tituloFiltro do ViewModel")
        void filtroDeStatusRefletidoNoViewModel() {
            when(relatorioService.listarVendas(eq("CANCELADA"), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            service.gerarHistorico("CANCELADA");

            ArgumentCaptor<Context> ctxCaptor = ArgumentCaptor.forClass(Context.class);
            verify(pdfService).gerarPdf(eq("pdf/historico-vendas"), ctxCaptor.capture());
            HistoricoVendasView view = (HistoricoVendasView) ctxCaptor.getValue().getVariable("historico");

            assertEquals("Status: CANCELADA", view.getTituloFiltro());
        }

        @Test
        @DisplayName("lista vazia produz view com semDados=true e total geral zerado")
        void listaVaziaProduzViewComSemDadosTrue() {
            when(relatorioService.listarVendas(any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            service.gerarHistorico(null);

            ArgumentCaptor<Context> ctxCaptor = ArgumentCaptor.forClass(Context.class);
            verify(pdfService).gerarPdf(eq("pdf/historico-vendas"), ctxCaptor.capture());
            HistoricoVendasView view = (HistoricoVendasView) ctxCaptor.getValue().getVariable("historico");

            assertTrue(view.isSemDados());
            assertEquals(0, view.getQtdeVendas());
            assertEquals("Todos os status", view.getTituloFiltro());
            assertTrue(view.getTotalGeralFormatado().contains("0,00"),
                    "total geral zero: " + view.getTotalGeralFormatado());
        }
    }

    private static RelatorioVendaDTO exemploVenda(Long id, String status) {
        return new RelatorioVendaDTO(
                id,
                Instant.parse("2026-06-30T17:30:00Z"),
                new BigDecimal("42.50"),
                "PIX",
                status,
                "Maria Operadora"
        );
    }
}
