package vexon.sellionpdv.auditoria;

import java.util.Objects;
import java.util.UUID;

/** Entrada interna: não contém tenant, ator, instante ou resultado, pois o backend os determina. */
public record AuditoriaEventoComando(TipoRecursoAuditavel tipoRecurso, Long recursoId,
                                    ContratoAuditoria contrato, String motivo,
                                    UUID correlationId) {
    public AuditoriaEventoComando {
        Objects.requireNonNull(tipoRecurso);
        Objects.requireNonNull(recursoId);
        Objects.requireNonNull(contrato);
        if (recursoId <= 0) {
            throw new IllegalArgumentException("O identificador do recurso auditado deve ser positivo.");
        }
    }
}
