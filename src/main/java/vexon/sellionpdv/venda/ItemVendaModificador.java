package vexon.sellionpdv.venda;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.TenantId;
import vexon.sellionpdv.modificador.OpcaoModificador;
import vexon.sellionpdv.tenant.Tenant;

import java.math.BigDecimal;

@Entity
@Table(name = "itens_venda_modificadores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemVendaModificador {

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
    @JoinColumn(name = "item_venda_id", nullable = false)
    private ItemVenda itemVenda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opcao_id", nullable = false)
    private OpcaoModificador opcao;

    @Builder.Default
    @Column(nullable = false)
    private Integer quantidade = 1;

    @Column(name = "preco_adicional_cobrado", nullable = false)
    private BigDecimal precoAdicionalCobrado;
}