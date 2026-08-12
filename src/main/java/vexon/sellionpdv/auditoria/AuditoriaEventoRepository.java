package vexon.sellionpdv.auditoria;

import java.util.UUID;
import org.springframework.data.repository.Repository;

/** A superfície de escrita não expõe operações de edição ou exclusão. */
public interface AuditoriaEventoRepository extends Repository<AuditoriaEvento, UUID> {
    AuditoriaEvento save(AuditoriaEvento evento);
}
