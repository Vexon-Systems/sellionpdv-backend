package vexon.sellionpdv.maquininha;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;

@Entity
@Table(name = "maquininhas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Maquininha {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String marca;

    @Column(name = "taxa_debito", nullable = false)
    private BigDecimal taxaDebito;

    @Column(name = "taxa_credito", nullable = false)
    private BigDecimal taxaCredito;

    @Builder.Default
    @Column(nullable = false)
    private Boolean ativo = true; // Necessário para o Soft-Delete
}