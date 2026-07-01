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
import vexon.sellionpdv.caixa.Caixa;
import vexon.sellionpdv.common.exception.ResourceNotFoundException;
import vexon.sellionpdv.modificador.OpcaoModificador;
import vexon.sellionpdv.produto.Produto;
import vexon.sellionpdv.tenant.Tenant;
import vexon.sellionpdv.usuario.Usuario;
import vexon.sellionpdv.venda.FormaPagamento;
import vexon.sellionpdv.venda.ItemVenda;
import vexon.sellionpdv.venda.ItemVendaModificador;
import vexon.sellionpdv.venda.StatusVenda;
import vexon.sellionpdv.venda.Venda;
import vexon.sellionpdv.venda.VendaRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReciboVendaPdfService")
class ReciboVendaPdfServiceTest {

    @Mock private VendaRepository vendaRepository;
    @Mock private PdfService pdfService;
    @InjectMocks private ReciboVendaPdfService service;

    @Nested
    @DisplayName("gerarRecibo")
    class GerarRecibo {

        @Test
        @DisplayName("lança ResourceNotFoundException quando a venda não existe")
        void lancaQuandoVendaInexistente() {
            when(vendaRepository.buscarReciboComDetalhes(99L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> service.gerarRecibo(99L));
            verifyNoInteractions(pdfService);
        }

        @Test
        @DisplayName("delega ao PdfService com template e ViewModel corretos")
        void delegaAoPdfServiceComTemplateEViewCorretos() {
            Venda venda = umaVendaConcluidaCompleta();
            when(vendaRepository.buscarReciboComDetalhes(1L)).thenReturn(Optional.of(venda));
            when(pdfService.gerarPdf(eq("pdf/recibo-venda"), any(Context.class)))
                    .thenReturn(new byte[]{1, 2, 3});

            byte[] resultado = service.gerarRecibo(1L);

            assertArrayEquals(new byte[]{1, 2, 3}, resultado);

            ArgumentCaptor<Context> ctxCaptor = ArgumentCaptor.forClass(Context.class);
            verify(pdfService).gerarPdf(eq("pdf/recibo-venda"), ctxCaptor.capture());
            ReciboVendaView view = (ReciboVendaView) ctxCaptor.getValue().getVariable("recibo");

            assertEquals("Franquia Teste", view.getNomeFantasia());
            assertEquals(1L, view.getVendaId());
            assertEquals("Operador", view.getOperador());
            assertEquals("DINHEIRO", view.getFormaPagamento());
            assertEquals("CONCLUIDA", view.getStatus());
            assertFalse(view.isCancelada());
            assertNull(view.getJustificativaCancelamento());
            assertEquals(1, view.getItens().size());
        }

        @Test
        @DisplayName("formata modificadores como '<nome> (+R$ X,XX)' quando há preço adicional")
        void formataModificadoresComPreco() {
            Venda venda = umaVendaConcluidaCompleta();
            ItemVenda item = venda.getItens().get(0);
            item.setModificadores(List.of(
                    modificadorCom("Extra Queijo", new BigDecimal("2.50")),
                    modificadorCom("Sem cebola", BigDecimal.ZERO)
            ));
            when(vendaRepository.buscarReciboComDetalhes(1L)).thenReturn(Optional.of(venda));

            service.gerarRecibo(1L);

            ArgumentCaptor<Context> ctxCaptor = ArgumentCaptor.forClass(Context.class);
            verify(pdfService).gerarPdf(eq("pdf/recibo-venda"), ctxCaptor.capture());
            ReciboVendaView view = (ReciboVendaView) ctxCaptor.getValue().getVariable("recibo");
            List<String> mods = view.getItens().get(0).getModificadores();

            assertEquals(2, mods.size());
            assertTrue(mods.get(0).startsWith("Extra Queijo (+"), "Esperava prefixo nome + (+ ... ): " + mods.get(0));
            assertTrue(mods.get(0).contains("2,50"), "Esperava valor formatado: " + mods.get(0));
            assertEquals("Sem cebola", mods.get(1));
        }

        @Test
        @DisplayName("marca como cancelada e preenche justificativa quando status é CANCELADA")
        void marcaJustificativaQuandoCancelada() {
            Venda venda = umaVendaConcluidaCompleta();
            venda.setStatus(StatusVenda.CANCELADA);
            venda.setJustificativaCancelamento("Cliente desistiu");
            venda.setDataCancelamento(OffsetDateTime.parse("2026-06-30T15:00:00-03:00"));
            when(vendaRepository.buscarReciboComDetalhes(1L)).thenReturn(Optional.of(venda));

            service.gerarRecibo(1L);

            ArgumentCaptor<Context> ctxCaptor = ArgumentCaptor.forClass(Context.class);
            verify(pdfService).gerarPdf(eq("pdf/recibo-venda"), ctxCaptor.capture());
            ReciboVendaView view = (ReciboVendaView) ctxCaptor.getValue().getVariable("recibo");

            assertTrue(view.isCancelada());
            assertEquals("CANCELADA", view.getStatus());
            assertEquals("Cliente desistiu", view.getJustificativaCancelamento());
            assertNotNull(view.getDataCancelamentoFormatada());
            assertFalse(view.getDataCancelamentoFormatada().isBlank());
        }
    }

    // ─── fixtures locais ────────────────────────────────────────────────────────

    private static Venda umaVendaConcluidaCompleta() {
        Tenant tenant = Tenant.builder().id(1L).nomeFantasia("Franquia Teste").build();
        Usuario operador = Usuario.builder().id(1L).nome("Operador").build();
        Caixa caixa = Caixa.builder().id(10L).tenant(tenant).operadorAbertura(operador).build();
        Produto produto = Produto.builder().id(1L).nome("X-Burguer").precoBase(new BigDecimal("20.00")).build();

        ItemVenda item = ItemVenda.builder()
                .id(100L)
                .produto(produto)
                .quantidade(1)
                .precoUnitarioCobrado(new BigDecimal("20.00"))
                .subtotalItem(new BigDecimal("20.00"))
                .modificadores(new java.util.ArrayList<>())
                .build();

        Venda venda = Venda.builder()
                .id(1L)
                .tenant(tenant)
                .caixa(caixa)
                .status(StatusVenda.CONCLUIDA)
                .formaPagamento(FormaPagamento.DINHEIRO)
                .subtotal(new BigDecimal("20.00"))
                .descontoAplicado(BigDecimal.ZERO)
                .totalFinal(new BigDecimal("20.00"))
                .dataVenda(OffsetDateTime.parse("2026-06-30T14:30:00-03:00"))
                .itens(new java.util.ArrayList<>())
                .build();

        item.setVenda(venda);
        venda.getItens().add(item);
        return venda;
    }

    private static ItemVendaModificador modificadorCom(String nome, BigDecimal precoAdicional) {
        OpcaoModificador opcao = OpcaoModificador.builder().id(1L).nome(nome).precoAdicional(precoAdicional).build();
        return ItemVendaModificador.builder()
                .opcao(opcao)
                .quantidade(1)
                .precoAdicionalCobrado(precoAdicional)
                .build();
    }
}
