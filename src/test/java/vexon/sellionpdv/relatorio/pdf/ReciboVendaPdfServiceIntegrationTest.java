package vexon.sellionpdv.relatorio.pdf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import vexon.sellionpdv.caixa.Caixa;
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
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Smoke test que monta toda a cadeia (TemplateEngine real → PdfService real → ReciboVendaPdfService)
 * com apenas o repositório mockado. Garante que o template renderiza e o OpenHTMLtoPDF produz
 * um PDF válido a partir de uma Venda real.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReciboVendaPdfService — integração (template + PDF real)")
class ReciboVendaPdfServiceIntegrationTest {

    @Mock private VendaRepository vendaRepository;
    private ReciboVendaPdfService service;

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
        service = new ReciboVendaPdfService(vendaRepository, pdfService);
    }

    @Test
    @DisplayName("gera PDF válido para venda concluída com modificadores")
    void geraPdfParaVendaConcluida() {
        when(vendaRepository.buscarReciboComDetalhes(1L))
                .thenReturn(Optional.of(vendaCompletaComModificadores()));

        byte[] bytes = service.gerarRecibo(1L);

        assertNotNull(bytes);
        assertTrue(bytes.length > 0, "PDF não pode estar vazio");
        String header = new String(bytes, 0, 5, StandardCharsets.US_ASCII);
        assertEquals("%PDF-", header, "Assinatura PDF inválida");
    }

    @Test
    @DisplayName("gera PDF válido para venda cancelada (com banner e justificativa)")
    void geraPdfParaVendaCancelada() {
        Venda venda = vendaCompletaComModificadores();
        venda.setStatus(StatusVenda.CANCELADA);
        venda.setJustificativaCancelamento("Cliente desistiu da compra");
        venda.setDataCancelamento(OffsetDateTime.parse("2026-06-30T15:00:00-03:00"));
        when(vendaRepository.buscarReciboComDetalhes(1L)).thenReturn(Optional.of(venda));

        byte[] bytes = service.gerarRecibo(1L);

        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
        assertEquals("%PDF-", new String(bytes, 0, 5, StandardCharsets.US_ASCII));
    }

    @Test
    @DisplayName("gera PDF válido quando a venda tem múltiplos itens (outer loop itera mais de 1x)")
    void geraPdfParaVendaComMultiplosItens() {
        when(vendaRepository.buscarReciboComDetalhes(1L))
                .thenReturn(Optional.of(vendaComMultiplosItens()));

        byte[] bytes = service.gerarRecibo(1L);

        assertEquals("%PDF-", new String(bytes, 0, 5, StandardCharsets.US_ASCII));
        assertTrue(bytes.length > 0);
    }

    @Test
    @DisplayName("gera PDF válido quando itens não têm modificadores (inner loop sobre lista vazia)")
    void geraPdfParaVendaSemModificadores() {
        when(vendaRepository.buscarReciboComDetalhes(1L))
                .thenReturn(Optional.of(vendaSemModificadores()));

        byte[] bytes = service.gerarRecibo(1L);

        assertEquals("%PDF-", new String(bytes, 0, 5, StandardCharsets.US_ASCII));
        assertTrue(bytes.length > 0);
    }

    // ─── fixtures ───────────────────────────────────────────────────────────────

    private static Venda vendaComMultiplosItens() {
        Tenant tenant = Tenant.builder().id(1L).nomeFantasia("Lanchonete da Esquina").build();
        Usuario operador = Usuario.builder().id(1L).nome("Maria Operadora").build();
        Caixa caixa = Caixa.builder().id(10L).tenant(tenant).operadorAbertura(operador).build();

        Produto burger = Produto.builder().id(1L).nome("X-Burguer").precoBase(new BigDecimal("20.00")).build();
        Produto batata = Produto.builder().id(2L).nome("Batata Frita G").precoBase(new BigDecimal("15.00")).build();
        Produto refri = Produto.builder().id(3L).nome("Refrigerante 500ml").precoBase(new BigDecimal("8.00")).build();

        List<ItemVenda> itens = new ArrayList<>();
        itens.add(itemSemMods(burger, 2, new BigDecimal("20.00"), new BigDecimal("40.00")));
        itens.add(itemSemMods(batata, 1, new BigDecimal("15.00"), new BigDecimal("15.00")));
        itens.add(itemSemMods(refri, 1, new BigDecimal("8.00"), new BigDecimal("8.00")));

        Venda venda = Venda.builder()
                .id(1L).tenant(tenant).caixa(caixa)
                .status(StatusVenda.CONCLUIDA).formaPagamento(FormaPagamento.CREDITO)
                .subtotal(new BigDecimal("63.00")).descontoAplicado(BigDecimal.ZERO).totalFinal(new BigDecimal("63.00"))
                .dataVenda(OffsetDateTime.parse("2026-06-30T14:30:00-03:00"))
                .itens(itens).build();
        itens.forEach(it -> it.setVenda(venda));
        return venda;
    }

    private static Venda vendaSemModificadores() {
        Tenant tenant = Tenant.builder().id(1L).nomeFantasia("Lanchonete da Esquina").build();
        Usuario operador = Usuario.builder().id(1L).nome("Maria Operadora").build();
        Caixa caixa = Caixa.builder().id(10L).tenant(tenant).operadorAbertura(operador).build();
        Produto produto = Produto.builder().id(1L).nome("Pizza Marguerita").precoBase(new BigDecimal("35.00")).build();

        ItemVenda item = itemSemMods(produto, 1, new BigDecimal("35.00"), new BigDecimal("35.00"));

        Venda venda = Venda.builder()
                .id(1L).tenant(tenant).caixa(caixa)
                .status(StatusVenda.CONCLUIDA).formaPagamento(FormaPagamento.PIX)
                .subtotal(new BigDecimal("35.00")).descontoAplicado(BigDecimal.ZERO).totalFinal(new BigDecimal("35.00"))
                .dataVenda(OffsetDateTime.parse("2026-06-30T14:30:00-03:00"))
                .itens(new ArrayList<>(List.of(item))).build();
        item.setVenda(venda);
        return venda;
    }

    private static ItemVenda itemSemMods(Produto produto, int qtde, BigDecimal precoUnit, BigDecimal subtotal) {
        return ItemVenda.builder()
                .produto(produto)
                .quantidade(qtde)
                .precoUnitarioCobrado(precoUnit)
                .subtotalItem(subtotal)
                .modificadores(new ArrayList<>())
                .build();
    }

    private static Venda vendaCompletaComModificadores() {
        Tenant tenant = Tenant.builder().id(1L).nomeFantasia("Lanchonete da Esquina").build();
        Usuario operador = Usuario.builder().id(1L).nome("Maria Operadora").build();
        Caixa caixa = Caixa.builder().id(10L).tenant(tenant).operadorAbertura(operador).build();
        Produto produto = Produto.builder().id(1L).nome("X-Tudo").precoBase(new BigDecimal("25.00")).build();

        OpcaoModificador extraQueijo = OpcaoModificador.builder().id(1L)
                .nome("Extra Queijo").precoAdicional(new BigDecimal("3.50")).build();
        OpcaoModificador semCebola = OpcaoModificador.builder().id(2L)
                .nome("Sem cebola").precoAdicional(BigDecimal.ZERO).build();

        ItemVenda item = ItemVenda.builder()
                .id(100L)
                .produto(produto)
                .quantidade(2)
                .precoUnitarioCobrado(new BigDecimal("28.50"))
                .subtotalItem(new BigDecimal("57.00"))
                .modificadores(new ArrayList<>())
                .build();

        item.getModificadores().add(ItemVendaModificador.builder()
                .opcao(extraQueijo).quantidade(1).precoAdicionalCobrado(new BigDecimal("3.50"))
                .itemVenda(item).build());
        item.getModificadores().add(ItemVendaModificador.builder()
                .opcao(semCebola).quantidade(1).precoAdicionalCobrado(BigDecimal.ZERO)
                .itemVenda(item).build());

        Venda venda = Venda.builder()
                .id(1L)
                .tenant(tenant)
                .caixa(caixa)
                .status(StatusVenda.CONCLUIDA)
                .formaPagamento(FormaPagamento.DINHEIRO)
                .subtotal(new BigDecimal("57.00"))
                .descontoAplicado(new BigDecimal("5.00"))
                .totalFinal(new BigDecimal("52.00"))
                .dataVenda(OffsetDateTime.parse("2026-06-30T14:30:00-03:00"))
                .itens(new ArrayList<>())
                .build();
        item.setVenda(venda);
        venda.getItens().add(item);
        return venda;
    }
}
