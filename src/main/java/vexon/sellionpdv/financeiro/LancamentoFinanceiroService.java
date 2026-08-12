package vexon.sellionpdv.financeiro;

import lombok.RequiredArgsConstructor;
import java.time.Clock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import vexon.sellionpdv.common.exception.BusinessException;
import vexon.sellionpdv.common.exception.CodedHttpException;
import vexon.sellionpdv.common.exception.ResourceNotFoundException;
import vexon.sellionpdv.common.service.UsuarioContextService;
import vexon.sellionpdv.financeiro.dto.CancelamentoLancamentoRequestDTO;
import vexon.sellionpdv.financeiro.dto.LancamentoRequestDTO;
import vexon.sellionpdv.financeiro.dto.LancamentoCriacaoResultado;
import vexon.sellionpdv.financeiro.dto.LancamentoResponseDTO;
import vexon.sellionpdv.tenant.TenantContext;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LancamentoFinanceiroService {

    private final LancamentoFinanceiroRepository repository;
    private final LancamentoFinanceiroPersistenciaService persistenciaService;
    private final UsuarioContextService usuarioContextService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<LancamentoResponseDTO> listarPorPeriodo(LocalDate dataInicial, LocalDate dataFinal) {
        return repository.findByDataReferenciaBetweenOrderByDataReferenciaDesc(dataInicial, dataFinal)
                .stream()
                .map(LancamentoResponseDTO::new)
                .toList();
    }

    public LancamentoCriacaoResultado criar(LancamentoRequestDTO dto, UUID idempotencyKey) {
        Long tenantId = TenantContext.getCurrentTenant();
        String payloadHash = gerarHashPayload(dto);

        var existente = repository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
        if (existente.isPresent()) {
            return resultadoDoExistente(existente.get(), payloadHash);
        }

        LancamentoFinanceiro lancamento = LancamentoFinanceiro.builder()
                .tenantId(tenantId)
                .descricao(dto.descricao())
                .valor(dto.valor())
                .categoria(dto.categoria())
                .dataReferencia(dto.dataReferencia())
                .idempotencyKey(idempotencyKey)
                .idempotencyPayloadHash(payloadHash)
                .build();

        try {
            return new LancamentoCriacaoResultado(
                    new LancamentoResponseDTO(persistenciaService.persistir(lancamento)), false);
        } catch (DataIntegrityViolationException ex) {
            LancamentoFinanceiro concorrente = repository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)
                    .orElseThrow(() -> ex);
            return resultadoDoExistente(concorrente, payloadHash);
        }
    }

    @Transactional
    public LancamentoResponseDTO atualizar(Long id, LancamentoRequestDTO dto) {
        LancamentoFinanceiro lancamento = repository.findByIdAndTenantId(id, TenantContext.getCurrentTenant())
                .orElseThrow(() -> new ResourceNotFoundException("Lançamento não encontrado."));

        if (lancamento.getStatus() == StatusLancamentoFinanceiro.CANCELADO) {
            throw new BusinessException("Não é possível editar um lançamento cancelado.");
        }

        lancamento.setDescricao(dto.descricao());
        lancamento.setValor(dto.valor());
        lancamento.setCategoria(dto.categoria());
        lancamento.setDataReferencia(dto.dataReferencia());

        return new LancamentoResponseDTO(repository.save(lancamento));
    }

    @Transactional
    public LancamentoResponseDTO cancelar(Long id, CancelamentoLancamentoRequestDTO dto) {
        var ator = usuarioContextService.getUsuarioAutenticado();
        Long tenantId = ator.getTenant() == null ? null : ator.getTenant().getId();
        if (tenantId == null || !tenantId.equals(TenantContext.getCurrentTenant())) {
            throw new AccessDeniedException("Usuário autenticado sem tenant válido.");
        }

        LancamentoFinanceiro lancamento = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Lançamento não encontrado."));
        if (lancamento.getStatus() == StatusLancamentoFinanceiro.CANCELADO) {
            throw new BusinessException("Este lançamento já está cancelado.");
        }

        String motivo = normalizarMotivo(dto);
        lancamento.setStatus(StatusLancamentoFinanceiro.CANCELADO);
        lancamento.setMotivoCancelamento(motivo);
        lancamento.setDataCancelamento(OffsetDateTime.now(clock));
        lancamento.setUsuarioCancelamento(ator);

        return new LancamentoResponseDTO(repository.save(lancamento));
    }

    private String normalizarMotivo(CancelamentoLancamentoRequestDTO dto) {
        if (dto == null || dto.motivo() == null) {
            throw new BusinessException("O motivo do cancelamento é obrigatório.");
        }
        String motivo = dto.motivo().trim();
        if (motivo.length() < 3 || motivo.length() > 500) {
            throw new BusinessException("O motivo do cancelamento deve ter entre 3 e 500 caracteres.");
        }
        return motivo;
    }

    private LancamentoCriacaoResultado resultadoDoExistente(LancamentoFinanceiro existente, String payloadHash) {
        if (!payloadHash.equals(existente.getIdempotencyPayloadHash())) {
            throw new CodedHttpException(org.springframework.http.HttpStatus.CONFLICT,
                    "IDEMPOTENCY_KEY_REUSED",
                    "A chave de idempotência já foi usada com outro lançamento.");
        }
        return new LancamentoCriacaoResultado(new LancamentoResponseDTO(existente), true);
    }

    private String gerarHashPayload(LancamentoRequestDTO dto) {
        String descricao = Normalizer.normalize(dto.descricao().trim(), Normalizer.Form.NFC);
        String valor = dto.valor().stripTrailingZeros().toPlainString();
        String representacaoCanonica = String.join("\n", descricao, valor, dto.categoria().name(),
                dto.dataReferencia().toString());
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(representacaoCanonica.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 não está disponível na JVM.", ex);
        }
    }
}
