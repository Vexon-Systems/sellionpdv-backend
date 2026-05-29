package vexon.sellionpdv.venda;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.TenantId;
import vexon.sellionpdv.produto.Produto;
import vexon.sellionpdv.tenant.Tenant;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "itens_venda")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemVenda {

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
    @JoinColumn(name = "venda_id", nullable = false)
    private Venda venda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(nullable = false)
    private Integer quantidade;

    @Column(name = "preco_unitario_cobrado", nullable = false)
    private BigDecimal precoUnitarioCobrado;

    @Column(name = "subtotal_item", nullable = false)
    private BigDecimal subtotalItem;

    @Builder.Default
    @OneToMany(mappedBy = "itemVenda", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemVendaModificador> modificadores = new ArrayList<>();
}
