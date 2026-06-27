package vexon.sellionpdv.relatorio;

import vexon.sellionpdv.caixa.Caixa;
import vexon.sellionpdv.caixa.StatusCaixa;
import vexon.sellionpdv.financeiro.CategoriaLancamento;
import vexon.sellionpdv.financeiro.LancamentoFinanceiro;
import vexon.sellionpdv.maquininha.Maquininha;
import vexon.sellionpdv.produto.Produto;
import vexon.sellionpdv.usuario.Usuario;
import vexon.sellionpdv.venda.FormaPagamento;
import vexon.sellionpdv.venda.ItemVenda;
import vexon.sellionpdv.venda.StatusVenda;
import vexon.sellionpdv.venda.Venda;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Fixtures para RelatorioServiceTest.
 *
 * Os métodos de Venda constroem o grafo mínimo necessário para o mapeamento do
 * RelatorioService: Caixa → Usuario(operadorAbertura). Campos sem acesso no
 * Service (subtotal, descontoAplicado, idempotencyKey) são deixados nulos
 * intencionalmente — nunca são persistidos, apenas lidos.
 */
public final class RelatorioTestFixtures {

    private RelatorioTestFixtures() {}

    // ─── Blocos base ──────────────────────────────────────────────────────────────

    /**
     * Tenant omitido intencionalmente — RelatorioService acessa apenas operador.getNome(),
     * nunca operador.getTenant(). Incluir Tenant aqui criaria acoplamento desnecessário.
     */
    public static Usuario umOperador() {
        return Usuario.builder()
                .id(1L)
                .nome("Operador Teste")
                .email("operador@test.com")
                .senhaHash("hash")
                .role("ROLE_ADMIN")
                .ativo(true)
                .build();
    }

    public static Caixa umCaixa(Usuario operador) {
        return Caixa.builder()
                .id(1L)
                .status(StatusCaixa.ABERTO)
                .dataAbertura(OffsetDateTime.now())
                .saldoInicial(BigDecimal.ZERO)
                .operadorAbertura(operador)
                .build();
    }

    private static Produto umProduto() {
        return Produto.builder().id(1L).nome("Produto Teste").build();
    }

    // ─── Vendas ───────────────────────────────────────────────────────────────────

    /** Venda CONCLUIDA sem maquininha e sem itens pré-carregados. */
    public static Venda umaVendaConcluida(BigDecimal total, FormaPagamento fp) {
        return Venda.builder()
                .id(1L)
                .caixa(umCaixa(umOperador()))
                .status(StatusVenda.CONCLUIDA)
                .formaPagamento(fp)
                .totalFinal(total)
                .dataVenda(OffsetDateTime.now())
                .itens(new java.util.ArrayList<>())
                .build();
    }

    /** Venda CONCLUIDA com maquininha associada (para cálculo de taxas no DRE). */
    public static Venda umaVendaConcluidaComMaquininha(BigDecimal total, FormaPagamento fp,
                                                        Maquininha maquininha) {
        return Venda.builder()
                .id(2L)
                .caixa(umCaixa(umOperador()))
                .status(StatusVenda.CONCLUIDA)
                .formaPagamento(fp)
                .maquininha(maquininha)
                .totalFinal(total)
                .dataVenda(OffsetDateTime.now())
                .itens(new java.util.ArrayList<>())
                .build();
    }

    /** Venda CANCELADA. totalFinal entra em receitaBruta e totalCancelamentos no DRE. */
    public static Venda umaVendaCancelada(BigDecimal total) {
        return Venda.builder()
                .id(3L)
                .caixa(umCaixa(umOperador()))
                .status(StatusVenda.CANCELADA)
                .formaPagamento(FormaPagamento.DINHEIRO)
                .totalFinal(total)
                .dataVenda(OffsetDateTime.now())
                .itens(new java.util.ArrayList<>())
                .build();
    }

    // ─── Itens de venda ───────────────────────────────────────────────────────────

    /**
     * Item com custoEstimadoUnitario=null.
     * Testa a proteção do DRE: "custo != null ? custo : ZERO".
     */
    public static ItemVenda umItemSemCusto(Venda venda, int qtd) {
        return ItemVenda.builder()
                .id(1L)
                .venda(venda)
                .produto(umProduto())
                .quantidade(qtd)
                .precoUnitarioCobrado(new BigDecimal("10.00"))
                .subtotalItem(new BigDecimal("10.00").multiply(BigDecimal.valueOf(qtd)))
                // custoEstimadoUnitario omitido intencionalmente → null
                .build();
    }

    /** Item com custoEstimadoUnitario definido. CMV = custo * qtd. */
    public static ItemVenda umItemComCusto(Venda venda, BigDecimal custo, int qtd) {
        return ItemVenda.builder()
                .id(2L)
                .venda(venda)
                .produto(umProduto())
                .quantidade(qtd)
                .precoUnitarioCobrado(new BigDecimal("10.00"))
                .subtotalItem(new BigDecimal("10.00").multiply(BigDecimal.valueOf(qtd)))
                .custoEstimadoUnitario(custo)
                .build();
    }

    // ─── Maquininha ───────────────────────────────────────────────────────────────

    /**
     * taxaDebito e taxaCredito são valores PERCENTUAIS (ex.: 2.00 = 2%).
     * O DRE divide por 100 internamente: valorTaxa = total * taxa / 100.
     */
    public static Maquininha umaMaquininha(BigDecimal taxaDebito, BigDecimal taxaCredito) {
        return Maquininha.builder()
                .id(1L)
                .nome("PagSeguro")
                .marca("PagSeguro")
                .taxaDebito(taxaDebito)
                .taxaCredito(taxaCredito)
                .build();
    }

    // ─── Lançamento financeiro ────────────────────────────────────────────────────

    public static LancamentoFinanceiro umLancamento(CategoriaLancamento categoria,
                                                     BigDecimal valor,
                                                     LocalDate data,
                                                     Long id) {
        return LancamentoFinanceiro.builder()
                .id(id)
                .descricao("Lançamento " + categoria.name())
                .valor(valor)
                .categoria(categoria)
                .dataReferencia(data)
                .build();
    }
}
