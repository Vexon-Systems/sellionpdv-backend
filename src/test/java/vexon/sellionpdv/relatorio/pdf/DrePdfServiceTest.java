package vexon.sellionpdv.relatorio.pdf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.context.Context;
import vexon.sellionpdv.relatorio.RelatorioService;
import vexon.sellionpdv.relatorio.dto.DreCustosDTO;
import vexon.sellionpdv.relatorio.dto.DreDeducoesDTO;
import vexon.sellionpdv.relatorio.dto.DreDespesasOperacionaisDTO;
import vexon.sellionpdv.relatorio.dto.DreResponseDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DrePdfService")
class DrePdfServiceTest {

    @Mock private RelatorioService relatorioService;
    @Mock private PdfService pdfService;
    @InjectMocks private DrePdfService service;

    private static final LocalDate INICIO = LocalDate.of(2026, 6, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 6, 30);

    @Nested
    @DisplayName("gerarDre")
    class GerarDre {

        @Test
        @DisplayName("delega ao RelatorioService e chama PdfService com o template correto")
        void delegaAoPdfServiceComTemplateCorreto() {
            when(relatorioService.gerarDreGerencial(INICIO, FIM)).thenReturn(dreExemplo());
            when(pdfService.gerarPdf(eq("pdf/dre"), any(Context.class))).thenReturn(new byte[]{1, 2, 3});

            byte[] resultado = service.gerarDre(INICIO, FIM);

            assertArrayEquals(new byte[]{1, 2, 3}, resultado);
            verify(relatorioService).gerarDreGerencial(INICIO, FIM);
            verify(pdfService).gerarPdf(eq("pdf/dre"), any(Context.class));
        }

        @Test
        @DisplayName("formata valores monetários (R$ pt-BR) e percentuais no ViewModel")
        void formataValoresMonetariosEPercentuais() {
            when(relatorioService.gerarDreGerencial(INICIO, FIM)).thenReturn(dreExemplo());

            service.gerarDre(INICIO, FIM);

            ArgumentCaptor<Context> ctxCaptor = ArgumentCaptor.forClass(Context.class);
            verify(pdfService).gerarPdf(eq("pdf/dre"), ctxCaptor.capture());
            DreView view = (DreView) ctxCaptor.getValue().getVariable("dre");

            assertEquals("01/06/2026 a 30/06/2026", view.getPeriodo());
            assertTrue(view.getReceitaBrutaFormatada().contains("1.000,00"),
                    "esperava valor pt-BR: " + view.getReceitaBrutaFormatada());
            assertEquals("30,00%", view.getMargemBrutaFormatada());
            assertFalse(view.isLucroBrutoNegativo());
            assertFalse(view.isLucroLiquidoNegativo());
            assertFalse(view.isSemDespesas());
            assertEquals(1, view.getDespesas().size());
            assertEquals("ALUGUEL", view.getDespesas().get(0).getCategoria());
        }

        @Test
        @DisplayName("marca lucro líquido negativo quando total é abaixo de zero")
        void marcaLucroLiquidoNegativoQuandoNegativo() {
            DreResponseDTO dtoNegativo = new DreResponseDTO(
                    "01/06/2026 a 30/06/2026",
                    new BigDecimal("500.00"),
                    new DreDeducoesDTO(BigDecimal.ZERO, BigDecimal.ZERO),
                    new BigDecimal("500.00"),
                    new DreCustosDTO(new BigDecimal("400.00")),
                    new BigDecimal("100.00"),
                    20.0,
                    List.of(new DreDespesasOperacionaisDTO("ALUGUEL", new BigDecimal("300.00"))),
                    new BigDecimal("300.00"),
                    new BigDecimal("-200.00"),
                    -40.0
            );
            when(relatorioService.gerarDreGerencial(INICIO, FIM)).thenReturn(dtoNegativo);

            service.gerarDre(INICIO, FIM);

            ArgumentCaptor<Context> ctxCaptor = ArgumentCaptor.forClass(Context.class);
            verify(pdfService).gerarPdf(eq("pdf/dre"), ctxCaptor.capture());
            DreView view = (DreView) ctxCaptor.getValue().getVariable("dre");

            assertFalse(view.isLucroBrutoNegativo(), "lucro bruto 100.00 é positivo");
            assertTrue(view.isLucroLiquidoNegativo(), "lucro líquido -200.00 é negativo");
            assertEquals("-40,00%", view.getMargemLiquidaFormatada());
        }
    }

    // ─── fixture ────────────────────────────────────────────────────────────────

    private static DreResponseDTO dreExemplo() {
        return new DreResponseDTO(
                "01/06/2026 a 30/06/2026",
                new BigDecimal("1000.00"),
                new DreDeducoesDTO(new BigDecimal("50.00"), new BigDecimal("30.00")),
                new BigDecimal("920.00"),
                new DreCustosDTO(new BigDecimal("620.00")),
                new BigDecimal("300.00"),
                30.0,
                List.of(new DreDespesasOperacionaisDTO("ALUGUEL", new BigDecimal("150.00"))),
                new BigDecimal("150.00"),
                new BigDecimal("150.00"),
                15.0
        );
    }
}
