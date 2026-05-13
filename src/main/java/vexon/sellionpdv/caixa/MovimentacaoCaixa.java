package vexon.sellionpdv.caixa;

import jakarta.persistence.*;
import vexon.sellionpdv.tenant.Tenant;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "movimentacoes_caixa")
public class MovimentacaoCaixa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caixa_id", nullable = false)
    private Caixa caixa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoMovimentacaoCaixa tipo;

    @Column(nullable = false)
    private BigDecimal valor;

    @Column(nullable = false)
    private String motivo;

    @Column(name = "data_movimentacao")
    private OffsetDateTime dataMovimentacao;

    public MovimentacaoCaixa() {
    }

    public Long getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }

    public Caixa getCaixa() {
        return caixa;
    }

    public void setCaixa(Caixa caixa) {
        this.caixa = caixa;
    }

    public TipoMovimentacaoCaixa getTipo() {
        return tipo;
    }

    public void setTipo(TipoMovimentacaoCaixa tipo) {
        this.tipo = tipo;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public OffsetDateTime getDataMovimentacao() {
        return dataMovimentacao;
    }

    public void setDataMovimentacao(OffsetDateTime dataMovimentacao) {
        this.dataMovimentacao = dataMovimentacao;
    }
}