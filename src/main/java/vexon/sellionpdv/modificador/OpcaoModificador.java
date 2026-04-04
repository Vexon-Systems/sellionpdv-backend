package vexon.sellionpdv.modificador;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;

@Entity
@Table(name = "opcoes_modificadores")
@SQLRestriction("ativo = true")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class OpcaoModificador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_id", nullable = false)
    private GrupoModificador grupo;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Builder.Default
    @Column(name = "preco_adicional")
    private BigDecimal precoAdicional = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;
}
