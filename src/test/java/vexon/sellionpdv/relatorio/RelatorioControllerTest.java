package vexon.sellionpdv.relatorio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vexon.sellionpdv.config.GlobalExceptionHandler;
import vexon.sellionpdv.relatorio.pdf.CaixasPdfService;
import vexon.sellionpdv.relatorio.pdf.DrePdfService;
import vexon.sellionpdv.relatorio.pdf.HistoricoVendasPdfService;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cenários HTTP do RelatorioController focados no endpoint de PDF do DRE.
 * MockMvc standalone; role-based auth não é aplicada aqui (Spring Security fica fora do
 * setup standalone) — 401/403 pertencem à camada de filtros.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RelatorioController")
class RelatorioControllerTest {

    private MockMvc mockMvc;

    @Mock private RelatorioService relatorioService;
    @Mock private DrePdfService drePdfService;
    @Mock private HistoricoVendasPdfService historicoVendasPdfService;
    @Mock private CaixasPdfService caixasPdfService;

    @InjectMocks private RelatorioController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("GET /api/relatorios/dre.pdf")
    class BaixarDrePdf {

        @Test
        @DisplayName("Deve retornar 200 com application/pdf, Content-Disposition e Cache-Control")
        void deve_Retornar200_comPdfEHeadersCorretos() throws Exception {
            byte[] pdfFake = "%PDF-1.4 fake".getBytes();
            when(drePdfService.gerarDre(eq(LocalDate.of(2026, 6, 1)), eq(LocalDate.of(2026, 6, 30))))
                    .thenReturn(pdfFake);

            mockMvc.perform(get("/api/relatorios/dre.pdf")
                            .param("dataInicial", "2026-06-01")
                            .param("dataFinal", "2026-06-30"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                    .andExpect(header().string("Content-Disposition",
                            "attachment; filename=\"dre-2026-06-01-a-2026-06-30.pdf\""))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().bytes(pdfFake));
        }

        @Test
        @DisplayName("Deve retornar 422 quando dataFinal é anterior a dataInicial")
        void deve_Retornar422_quando_DataFinalAnteriorAInicial() throws Exception {
            mockMvc.perform(get("/api/relatorios/dre.pdf")
                            .param("dataInicial", "2026-06-30")
                            .param("dataFinal", "2026-06-01"))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    @Nested
    @DisplayName("GET /api/relatorios/vendas.pdf")
    class BaixarHistoricoVendasPdf {

        @Test
        @DisplayName("Deve retornar 200 com PDF e headers corretos sem filtro de status")
        void deve_Retornar200_semFiltroStatus() throws Exception {
            byte[] pdfFake = "%PDF-1.4 fake".getBytes();
            when(historicoVendasPdfService.gerarHistorico(isNull())).thenReturn(pdfFake);

            mockMvc.perform(get("/api/relatorios/vendas.pdf"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                    .andExpect(header().string("Content-Disposition",
                            "attachment; filename=\"historico-vendas.pdf\""))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().bytes(pdfFake));
        }

        @Test
        @DisplayName("Deve retornar 200 e propagar o filtro de status ao service")
        void deve_Retornar200_comFiltroStatus() throws Exception {
            byte[] pdfFake = "%PDF-1.4 fake".getBytes();
            when(historicoVendasPdfService.gerarHistorico(eq("CONCLUIDA"))).thenReturn(pdfFake);

            mockMvc.perform(get("/api/relatorios/vendas.pdf").param("status", "CONCLUIDA"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                    .andExpect(content().bytes(pdfFake));
        }
    }

    @Nested
    @DisplayName("GET /api/relatorios/caixas.pdf")
    class BaixarCaixasPdf {

        @Test
        @DisplayName("Deve retornar 200 com PDF e headers corretos")
        void deve_Retornar200_comPdfEHeadersCorretos() throws Exception {
            byte[] pdfFake = "%PDF-1.4 fake".getBytes();
            when(caixasPdfService.gerarCaixas(
                    eq(LocalDate.of(2026, 6, 1)), eq(LocalDate.of(2026, 6, 30))))
                    .thenReturn(pdfFake);

            mockMvc.perform(get("/api/relatorios/caixas.pdf")
                            .param("dataInicial", "2026-06-01")
                            .param("dataFinal", "2026-06-30"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                    .andExpect(header().string("Content-Disposition",
                            "attachment; filename=\"caixas-2026-06-01-a-2026-06-30.pdf\""))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().bytes(pdfFake));
        }

        @Test
        @DisplayName("Deve retornar 422 quando dataFinal é anterior a dataInicial")
        void deve_Retornar422_quando_DataFinalAnteriorAInicial() throws Exception {
            mockMvc.perform(get("/api/relatorios/caixas.pdf")
                            .param("dataInicial", "2026-06-30")
                            .param("dataFinal", "2026-06-01"))
                    .andExpect(status().isUnprocessableEntity());
        }
    }
}
