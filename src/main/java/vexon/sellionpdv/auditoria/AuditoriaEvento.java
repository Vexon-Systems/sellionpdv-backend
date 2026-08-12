package vexon.sellionpdv.auditoria;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "eventos_auditoria")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AuditoriaEvento {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "ator_usuario_id", nullable = false, updatable = false)
    private Long atorUsuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 50)
    private AcaoAuditoria acao;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_recurso", nullable = false, updatable = false, length = 50)
    private TipoRecursoAuditavel tipoRecurso;

    @Column(name = "recurso_id", nullable = false, updatable = false)
    private Long recursoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private ResultadoAuditoria resultado;

    @Column(name = "correlation_id", updatable = false)
    private UUID correlationId;

    @Column(columnDefinition = "text", updatable = false)
    private String motivo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "antes", updatable = false)
    private JsonNode antes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "depois", nullable = false, updatable = false)
    private JsonNode depois;

    @Column(name = "ocorrido_em", nullable = false, updatable = false)
    private Instant ocorridoEm;
}
