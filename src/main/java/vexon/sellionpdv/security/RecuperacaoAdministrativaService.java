package vexon.sellionpdv.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vexon.sellionpdv.auth.RefreshTokenService;
import vexon.sellionpdv.common.exception.BusinessException;
import vexon.sellionpdv.tenant.Tenant;
import vexon.sellionpdv.tenant.TenantRepository;
import vexon.sellionpdv.usuario.Usuario;
import vexon.sellionpdv.usuario.UsuarioRepository;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RecuperacaoAdministrativaService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final UsuarioRepository usuarioRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuditoriaRecuperacaoAdministrativaRepository auditoriaRepository;

    @Transactional
    public Long executar(Solicitacao solicitacao) {
        validarSolicitacao(solicitacao);
        Tenant tenant = tenantRepository.findById(solicitacao.tenantId())
                .orElseThrow(() -> new BusinessException("Tenant informado não encontrado."));

        Usuario usuario = usuarioRepository.findByEmailWithTenant(solicitacao.email()).orElse(null);
        String acao;
        if (usuario != null) {
            if (!usuario.getTenant().getId().equals(tenant.getId())
                    || !Boolean.TRUE.equals(usuario.getAtivo())
                    || !ROLE_ADMIN.equals(usuario.getRole())) {
                throw new BusinessException("A conta indicada não é um administrador ativo elegível neste tenant.");
            }
            usuario.setSenhaHash(passwordEncoder.encode(solicitacao.senhaTemporaria()));
            acao = "REDEFINIU_ADMIN";
        } else {
            if (usuarioRepository.existsByTenantAndRoleAndAtivoTrue(tenant, ROLE_ADMIN)) {
                throw new BusinessException("Já existe administrador ativo; informe uma conta administrativa elegível.");
            }
            usuario = Usuario.builder()
                    .tenant(tenant)
                    .nome("Administrador recuperado")
                    .email(solicitacao.email())
                    .senhaHash(passwordEncoder.encode(solicitacao.senhaTemporaria()))
                    .role(ROLE_ADMIN)
                    .ativo(true)
                    .build();
            acao = "CRIOU_ADMIN";
        }

        usuario.setDeveTrocarSenha(true);
        usuario.setSessaoInvalidaAntes(Instant.now());
        Usuario salvo = usuarioRepository.save(usuario);
        refreshTokenService.revogarTodosOsTokensAtivos(salvo);

        AuditoriaRecuperacaoAdministrativa evento = auditoriaRepository.save(
                AuditoriaRecuperacaoAdministrativa.builder()
                        .tenantId(tenant.getId())
                        .usuarioId(salvo.getId())
                        .operadorId(solicitacao.operadorId())
                        .chamadoId(solicitacao.chamadoId())
                        .acao(acao)
                        .ocorridoEm(Instant.now())
                        .build());
        return evento.getId();
    }

    private void validarSolicitacao(Solicitacao solicitacao) {
        if (solicitacao.email().isBlank() || solicitacao.operadorId().isBlank() || solicitacao.chamadoId().isBlank()) {
            throw new BusinessException("E-mail, operador e chamado são obrigatórios.");
        }
        String senha = solicitacao.senhaTemporaria();
        if (senha.length() < 24 || senha.chars().distinct().count() < 12) {
            throw new BusinessException("A senha temporária deve ter pelo menos 24 caracteres e alta diversidade.");
        }
    }

    public record Solicitacao(Long tenantId, String email, String senhaTemporaria,
                              String operadorId, String chamadoId) { }
}
