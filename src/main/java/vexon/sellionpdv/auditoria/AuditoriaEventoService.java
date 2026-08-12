package vexon.sellionpdv.auditoria;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vexon.sellionpdv.common.service.UsuarioContextService;
import vexon.sellionpdv.tenant.TenantContext;
import vexon.sellionpdv.usuario.Usuario;

@Service
@RequiredArgsConstructor
public class AuditoriaEventoService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final AuditoriaEventoRepository auditoriaEventoRepository;
    private final UsuarioContextService usuarioContextService;

    /**
     * Participa da transação chamadora dos fluxos de domínio. Uma falha de persistência
     * propaga como exceção e impede o commit da operação auditada.
     */
    @Transactional
    public AuditoriaEvento registrar(AuditoriaEventoComando comando) {
        validarContrato(comando);
        Usuario ator = usuarioContextService.getUsuarioAutenticado();
        Long tenantDoAtor = ator.getTenant().getId();
        validarTenantCorrente(tenantDoAtor);

        AuditoriaEvento evento = AuditoriaEvento.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantDoAtor)
                .atorUsuarioId(ator.getId())
                .acao(comando.contrato().acao())
                .tipoRecurso(comando.tipoRecurso())
                .recursoId(comando.recursoId())
                .resultado(ResultadoAuditoria.SUCESSO)
                .correlationId(comando.correlationId())
                .motivo(comando.motivo())
                .antes(OBJECT_MAPPER.valueToTree(comando.contrato().antes()))
                .depois(OBJECT_MAPPER.valueToTree(comando.contrato().depois()))
                .ocorridoEm(Instant.now())
                .build();

        return auditoriaEventoRepository.save(evento);
    }

    private void validarTenantCorrente(Long tenantDoAtor) {
        if (tenantDoAtor == null || !tenantDoAtor.equals(TenantContext.getCurrentTenant())) {
            throw new AccessDeniedException("O ator autenticado não pertence ao tenant corrente.");
        }
    }

    private void validarContrato(AuditoriaEventoComando comando) {
        TipoRecursoAuditavel esperado = switch (comando.contrato().acao()) {
            case VENDA_CRIADA, VENDA_CANCELADA -> TipoRecursoAuditavel.VENDA;
            case CAIXA_ABERTO, CAIXA_FECHADO -> TipoRecursoAuditavel.CAIXA;
            case CAIXA_REFORCO_REGISTRADO, CAIXA_SANGRIA_REGISTRADA -> TipoRecursoAuditavel.MOVIMENTACAO_CAIXA;
            case LANCAMENTO_FINANCEIRO_CRIADO, LANCAMENTO_FINANCEIRO_ALTERADO -> TipoRecursoAuditavel.LANCAMENTO_FINANCEIRO;
        };
        if (comando.tipoRecurso() != esperado) {
            throw new IllegalArgumentException("Ação de auditoria incompatível com o tipo de recurso.");
        }

        boolean aceitaMotivo = switch (comando.contrato().acao()) {
            case VENDA_CANCELADA, CAIXA_REFORCO_REGISTRADO, CAIXA_SANGRIA_REGISTRADA -> true;
            default -> false;
        };
        if (!aceitaMotivo && comando.motivo() != null) {
            throw new IllegalArgumentException("Motivo não é permitido para esta ação de auditoria.");
        }
    }
}
