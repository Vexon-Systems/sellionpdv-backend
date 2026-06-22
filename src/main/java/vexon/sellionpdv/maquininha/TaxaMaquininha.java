package vexon.sellionpdv.maquininha;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;

@Entity
@Table(
    name = "taxas_maquininha",
    uniqueConstraints = @UniqueConstraint(columnNames = {"maquininha_id", "bandeira", "tipo"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxaMaquininha {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maquininha_id", nullable = false)
    private Maquininha maquininha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BandeiraCartao bandeira;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoTransacaoCartao tipo;

    // Taxa percentual, ex: 1.99 = 1,99%. Precisão de 4 casas para suportar taxas como 1.9875%.
    @Column(nullable = false, precision = 8, scale = 4)
    private BigDecimal taxa;
}
