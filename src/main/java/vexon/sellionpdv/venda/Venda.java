package vexon.sellionpdv.venda;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.TenantId;
import vexon.sellionpdv.caixa.Caixa;
import vexon.sellionpdv.tenant.Tenant;

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

    @Column(name = "maquininha_id")
    private Long maquininhaId;

    @Column(nullable = false)
    private BigDecimal subtotal;

    @Column(name = "desconto_aplicado")
    private BigDecimal descontoAplicado;

    @Column(name = "total_final", nullable = false)
    private BigDecimal totalFinal;

    // Prevenção contra dupla cobrança (Falhas de Rede)
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private UUID idempotencyKey;

    @Column(name = "data_venda")
    private OffsetDateTime dataVenda;

    @Builder.Default
    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemVenda> itens = new ArrayList<>();
}