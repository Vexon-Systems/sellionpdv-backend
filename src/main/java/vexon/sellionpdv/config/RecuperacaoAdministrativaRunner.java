package vexon.sellionpdv.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import vexon.sellionpdv.common.exception.BusinessException;
import vexon.sellionpdv.security.RecuperacaoAdministrativaService;

/**
 * Job operacional sem HTTP. Deve ser executado isoladamente com
 * --spring.main.web-application-type=none e profiles prod,admin-recovery.
 * Nunca registra a senha temporária, nem em sucesso nem em falha.
 */
@Component
@Profile("admin-recovery")
@RequiredArgsConstructor
public class RecuperacaoAdministrativaRunner implements CommandLineRunner {

    private final RecuperacaoAdministrativaService service;

    @Value("${admin.recovery.enabled:false}")
    private boolean enabled;
    @Value("${admin.recovery.tenant-id:}")
    private String tenantId;
    @Value("${admin.recovery.email:}")
    private String email;
    @Value("${admin.recovery.temp-password:}")
    private String senhaTemporaria;
    @Value("${admin.recovery.operator-id:}")
    private String operadorId;
    @Value("${admin.recovery.ticket-id:}")
    private String chamadoId;

    @Override
    public void run(String... args) {
        if (!enabled) return;
        try {
            Long eventoId = service.executar(new RecuperacaoAdministrativaService.Solicitacao(
                    Long.parseLong(tenantId), email, senhaTemporaria, operadorId, chamadoId));
            System.out.println("Recuperação administrativa concluída. Evidência: " + eventoId);
        } catch (NumberFormatException exception) {
            throw new BusinessException("admin.recovery.tenant-id deve ser numérico.");
        }
    }
}
