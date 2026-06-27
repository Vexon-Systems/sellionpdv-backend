package vexon.sellionpdv.caixa;

import vexon.sellionpdv.caixa.dto.CaixaFechamentoRequestDTO;
import vexon.sellionpdv.caixa.dto.CaixaFechamentoResponseDTO;
import vexon.sellionpdv.caixa.dto.CaixaRequestDTO;
import vexon.sellionpdv.caixa.dto.MovimentacaoCaixaRequestDTO;
import vexon.sellionpdv.caixa.dto.MovimentacaoCaixaResponseDTO;
import vexon.sellionpdv.tenant.Tenant;
import vexon.sellionpdv.usuario.Usuario;
import vexon.sellionpdv.venda.FormaPagamento;
import vexon.sellionpdv.venda.StatusVenda;
import vexon.sellionpdv.venda.Venda;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class CaixaTestFixtures {

    private CaixaTestFixtures() {}

    // ─── entidades de domínio ───────────────────────────────────────────────────

    public static Tenant umTenant() {
        return Tenant.builder().id(1L).nomeFantasia("Franquia Teste").build();
    }

    public static Usuario umOperador(Tenant tenant) {
        return Usuario.builder()
                .id(1L)
                .email("operador@test.com")
                .nome("Operador")
                .tenant(tenant)
                .senhaHash("hash")
                .role("ROLE_ADMIN")
                .build();
    }

    public static Caixa umCaixaAberto(Tenant tenant) {
        return umCaixaAberto(tenant, new BigDecimal("100.00"));
    }

    public static Caixa umCaixaAberto(Tenant tenant, BigDecimal saldoInicial) {
        return Caixa.builder()
                .id(10L)
                .tenant(tenant)
                .status(StatusCaixa.ABERTO)
                .saldoInicial(saldoInicial)
                .dataAbertura(OffsetDateTime.now())
                .operadorAbertura(umOperador(tenant))
                .build();
    }

    public static MovimentacaoCaixa umaSangria(Caixa caixa, BigDecimal valor) {
        return MovimentacaoCaixa.builder()
                .id(1L)
                .caixa(caixa)
                .tipo(TipoMovimentacaoCaixa.SANGRIA)
                .valor(valor)
                .motivo("Sangria de caixa")
                .dataMovimentacao(OffsetDateTime.now())
                .build();
    }

    public static MovimentacaoCaixa umReforco(Caixa caixa, BigDecimal valor) {
        return MovimentacaoCaixa.builder()
                .id(2L)
                .caixa(caixa)
                .tipo(TipoMovimentacaoCaixa.REFORCO)
                .valor(valor)
                .motivo("Reforço de troco")
                .dataMovimentacao(OffsetDateTime.now())
                .build();
    }

    // Stubs mínimos de Venda para os streams de CaixaService.fecharCaixa().
    // fecharCaixa filtra por status e formaPagamento, e soma totalFinal —
    // esses são os únicos campos que precisam ser populados.
    public static Venda umaVendaConcluida(FormaPagamento formaPagamento, BigDecimal totalFinal) {
        return Venda.builder()
                .id(1L)
                .status(StatusVenda.CONCLUIDA)
                .formaPagamento(formaPagamento)
                .totalFinal(totalFinal)
                .subtotal(totalFinal)
                .descontoAplicado(BigDecimal.ZERO)
                .idempotencyKey(UUID.randomUUID())
                .dataVenda(OffsetDateTime.now())
                .build();
    }

    public static Venda umaVendaCancelada(BigDecimal totalFinal) {
        return Venda.builder()
                .id(2L)
                .status(StatusVenda.CANCELADA)
                .formaPagamento(FormaPagamento.DINHEIRO)
                .totalFinal(totalFinal)
                .subtotal(totalFinal)
                .descontoAplicado(BigDecimal.ZERO)
                .idempotencyKey(UUID.randomUUID())
                .dataVenda(OffsetDateTime.now())
                .build();
    }

    // ─── DTOs HTTP-layer ────────────────────────────────────────────────────────

    public static CaixaRequestDTO umCaixaRequestDTO(BigDecimal saldoInicial) {
        return new CaixaRequestDTO(saldoInicial);
    }

    public static CaixaFechamentoRequestDTO umCaixaFechamentoRequestDTO(BigDecimal saldoFinalInformado) {
        return new CaixaFechamentoRequestDTO(saldoFinalInformado);
    }

    public static MovimentacaoCaixaRequestDTO umaSangriaRequestDTO() {
        return new MovimentacaoCaixaRequestDTO(
                TipoMovimentacaoCaixa.SANGRIA,
                new BigDecimal("50.00"),
                "Sangria de caixa"
        );
    }

    public static MovimentacaoCaixaResponseDTO umaMovimentacaoResponseDTO() {
        return new MovimentacaoCaixaResponseDTO(
                1L,
                TipoMovimentacaoCaixa.SANGRIA,
                new BigDecimal("50.00"),
                "Sangria de caixa",
                OffsetDateTime.now()
        );
    }

    // Fixture com valores aritmeticamente consistentes:
    // saldoEsperado = saldoInicial(100) + totalTodasVendas(200) + reforcos(0) - sangrias(0) = 300
    // totalVendasDinheiro = 200 (todas as vendas concluídas são DINHEIRO neste cenário)
    // furoCaixa = saldoInformado(300) - saldoEsperado(300) = 0
    public static CaixaFechamentoResponseDTO umaCaixaFechamentoResponseDTO() {
        return new CaixaFechamentoResponseDTO(
                new BigDecimal("100.00"),
                new BigDecimal("200.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("300.00"),
                new BigDecimal("300.00"),
                BigDecimal.ZERO
        );
    }

    // ─── CaixaBuilder fluente ───────────────────────────────────────────────────
    //
    // Usado principalmente nos testes de fecharCaixa, onde o Caixa precisa
    // chegar pré-populado com uma lista de Vendas (caixa.getVendas()).
    // A coleção não vem do JPA nos testes unitários — o builder a injeta direto.

    public static CaixaBuilder umCaixa() {
        return new CaixaBuilder();
    }

    public static final class CaixaBuilder {

        private Long id = 10L;
        private Tenant tenant = umTenant();
        private BigDecimal saldoInicial = new BigDecimal("100.00");
        private List<Venda> vendas = List.of();
        private Usuario operadorAbertura = umOperador(umTenant());

        private CaixaBuilder() {}

        public CaixaBuilder comId(Long id)                    { this.id = id; return this; }
        public CaixaBuilder comTenant(Tenant tenant)          { this.tenant = tenant; return this; }
        public CaixaBuilder comSaldoInicial(BigDecimal saldo) { this.saldoInicial = saldo; return this; }
        public CaixaBuilder comVendas(List<Venda> vendas)     { this.vendas = vendas; return this; }
        public CaixaBuilder comOperador(Usuario operador)     { this.operadorAbertura = operador; return this; }

        public Caixa build() {
            return Caixa.builder()
                    .id(id)
                    .tenant(tenant)
                    .status(StatusCaixa.ABERTO)
                    .saldoInicial(saldoInicial)
                    .dataAbertura(OffsetDateTime.now())
                    .operadorAbertura(operadorAbertura)
                    .vendas(vendas)
                    .build();
        }
    }
}
