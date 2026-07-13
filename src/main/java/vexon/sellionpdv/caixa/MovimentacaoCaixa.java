package vexon.sellionpdv.caixa;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.TenantId;
import vexon.sellionpdv.tenant.Tenant;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "movimentacoes_caixa")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovimentacaoCaixa {

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
    private TipoMovimentacaoCaixa tipo;

    @Column(nullable = false)
    private BigDecimal valor;

    @Column(nullable = false)
    private String motivo;

    @Column(name = "data_movimentacao", nullable = false)
    private OffsetDateTime dataMovimentacao;

    @Column(name = "idempotency_key", unique = true)
    private UUID idempotencyKey;
}