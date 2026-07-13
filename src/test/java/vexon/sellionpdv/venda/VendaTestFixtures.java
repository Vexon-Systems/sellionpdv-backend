package vexon.sellionpdv.venda;

import vexon.sellionpdv.caixa.Caixa;
import vexon.sellionpdv.caixa.StatusCaixa;
import vexon.sellionpdv.modificador.OpcaoModificador;
import vexon.sellionpdv.produto.Produto;
import vexon.sellionpdv.tenant.Tenant;
import vexon.sellionpdv.usuario.Usuario;
import vexon.sellionpdv.venda.dto.ItemVendaRequestDTO;
import vexon.sellionpdv.venda.dto.VendaRequestDTO;
import vexon.sellionpdv.venda.dto.VendaResponseDTO;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class VendaTestFixtures {

    private VendaTestFixtures() {}

    // ─── entidades de domínio ───────────────────────────────────────────────────

    public static Tenant umTenant() {
        return Tenant.builder().id(1L).nomeFantasia("Franquia Teste").build();
    }

    public static Caixa umCaixaAberto(Tenant tenant) {
        return Caixa.builder()
                .id(10L)
                .tenant(tenant)
                .status(StatusCaixa.ABERTO)
                .saldoInicial(new BigDecimal("100.00"))
                .dataAbertura(OffsetDateTime.now())
                .build();
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

    public static Produto umProduto(BigDecimal preco) {
        return Produto.builder()
                .id(1L)
                .nome("X-Burguer")
                .precoBase(preco)
                .custoEstimado(new BigDecimal("5.00"))
                .build();
    }

    public static OpcaoModificador umaOpcao(Long id, BigDecimal precoAdicional) {
        return OpcaoModificador.builder()
                .id(id)
                .nome("Extra Queijo")
                .precoAdicional(precoAdicional)
                .build();
    }

    // ─── DTOs HTTP-layer (compartilhados com VendaControllerTest) ───────────────

    public static VendaRequestDTO umaVendaRequestDTOValida() {
        return new VendaRequestDTO(
                List.of(new ItemVendaRequestDTO(1L, 1, List.of())),
                FormaPagamento.DINHEIRO, null, null, null
        );
    }

    public static VendaResponseDTO umaVendaResponseDTO(UUID key) {
        return new VendaResponseDTO(
                1L,
                StatusVenda.CONCLUIDA,
                FormaPagamento.DINHEIRO,
                new BigDecimal("20.00"),
                BigDecimal.ZERO,
                new BigDecimal("20.00"),
                key,
                OffsetDateTime.now()
        );
    }

    // ─── VendaBuilder fluente ───────────────────────────────────────────────────

    public static VendaBuilder umaVenda() {
        return new VendaBuilder();
    }

    public static final class VendaBuilder {

        private Long id = 1L;
        private StatusVenda status = StatusVenda.CONCLUIDA;
        private FormaPagamento formaPagamento = FormaPagamento.DINHEIRO;
        private BigDecimal subtotal = new BigDecimal("20.00");
        private BigDecimal descontoAplicado = BigDecimal.ZERO;
        // null = calcular automaticamente como subtotal - descontoAplicado no build()
        private BigDecimal totalFinalExplicito = null;
        private UUID idempotencyKey = UUID.randomUUID();
        private OffsetDateTime dataVenda = OffsetDateTime.now();
        private String justificativaCancelamento = null;
        private OffsetDateTime dataCancelamento = null;
        // Toda Venda real tem caixa (nullable=false no banco) — ABERTO por padrão para
        // não quebrar os testes existentes; sobrescreva com comCaixa(...) para simular
        // cancelamento com caixa já FECHADO (SAST-08).
        private Caixa caixa = Caixa.builder().id(10L).status(StatusCaixa.ABERTO).build();

        private VendaBuilder() {}

        public VendaBuilder comId(Long id) { this.id = id; return this; }
        public VendaBuilder comStatus(StatusVenda status) { this.status = status; return this; }
        public VendaBuilder comFormaPagamento(FormaPagamento fp) { this.formaPagamento = fp; return this; }
        public VendaBuilder comSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; return this; }
        public VendaBuilder comDesconto(BigDecimal desconto) { this.descontoAplicado = desconto; return this; }
        /** Sobrescreve o cálculo automático — use apenas para simular dados inconsistentes. */
        public VendaBuilder comTotal(BigDecimal total) { this.totalFinalExplicito = total; return this; }
        public VendaBuilder comIdempotencyKey(UUID key) { this.idempotencyKey = key; return this; }
        public VendaBuilder comDataVenda(OffsetDateTime dataVenda) { this.dataVenda = dataVenda; return this; }
        public VendaBuilder comJustificativa(String justificativa) { this.justificativaCancelamento = justificativa; return this; }
        public VendaBuilder comDataCancelamento(OffsetDateTime dataCancelamento) { this.dataCancelamento = dataCancelamento; return this; }
        public VendaBuilder comCaixa(Caixa caixa) { this.caixa = caixa; return this; }

        public Venda build() {
            BigDecimal totalFinal = totalFinalExplicito != null
                    ? totalFinalExplicito
                    : subtotal.subtract(descontoAplicado);

            return Venda.builder()
                    .id(id)
                    .status(status)
                    .formaPagamento(formaPagamento)
                    .subtotal(subtotal)
                    .descontoAplicado(descontoAplicado)
                    .totalFinal(totalFinal)
                    .idempotencyKey(idempotencyKey)
                    .dataVenda(dataVenda)
                    .justificativaCancelamento(justificativaCancelamento)
                    .dataCancelamento(dataCancelamento)
                    .caixa(caixa)
                    .build();
        }
    }
}
