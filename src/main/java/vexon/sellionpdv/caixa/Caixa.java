package vexon.sellionpdv.caixa;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.TenantId;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "caixas")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Caixa {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "tenant_id")
    private Long tenantId;

    private String status;

    @Column(name = "data_abertura")
    private LocalDateTime dataAbertura;

    @Column(name = "data_fechamento")
    private LocalDateTime dataFechamento;

    @Column(name = "saldo_inicial")
    private BigDecimal saldoInicial;

    @Column(name = "saldo_final_informado")
    private BigDecimal saldoFinalInformado;

    @Column(name = "furo_caixa")
    private BigDecimal furoCaixa;
}