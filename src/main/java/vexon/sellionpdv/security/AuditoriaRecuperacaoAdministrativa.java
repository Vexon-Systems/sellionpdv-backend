package vexon.sellionpdv.security;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "auditoria_recuperacao_administrativa")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditoriaRecuperacaoAdministrativa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "operador_id", nullable = false)
    private String operadorId;

    @Column(name = "chamado_id", nullable = false)
    private String chamadoId;

    @Column(name = "acao", nullable = false)
    private String acao;

    @Column(name = "ocorrido_em", nullable = false)
    private Instant ocorridoEm;
}
