package vexon.sellionpdv.venda;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.TenantId;
import vexon.sellionpdv.caixa.Caixa;
import vexon.sellionpdv.maquininha.BandeiraCartao;
import vexon.sellionpdv.maquininha.Maquininha;
import vexon.sellionpdv.tenant.Tenant;
import vexon.sellionpdv.usuario.Usuario;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "vendas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Venda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "tenant_id")
    private Long tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", insertable = false, updatable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caixa_id", nullable = false)
    private Caixa caixa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusVenda status;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", nullable = false)
    private FormaPagamento formaPagamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maquininha_id")
    private Maquininha maquininha;

    @Enumerated(EnumType.STRING)
    @Column(name = "bandeira_cartao", length = 20)
    private BandeiraCartao bandeiraCartao;

    @Column(name = "justificativa_cancelamento", columnDefinition = "text")
    private String justificativaCancelamento;

    @Column(name = "data_cancelamento")
    private OffsetDateTime dataCancelamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_cancelamento_id")
    private Usuario usuarioCancelamento;

    @Column(nullable = false)
    private BigDecimal subtotal;

    @Column(name = "desconto_aplicado")
    private BigDecimal descontoAplicado;

    @Column(name = "motivo_desconto", length = 500)
    private String motivoDesconto;

    @Column(name = "total_final", nullable = false)
    private BigDecimal totalFinal;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private UUID idempotencyKey;

    @Column(name = "data_venda")
    private OffsetDateTime dataVenda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Builder.Default
    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemVenda> itens = new ArrayList<>();
}
