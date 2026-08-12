package vexon.sellionpdv.financeiro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import vexon.sellionpdv.common.exception.BusinessException;
import vexon.sellionpdv.common.exception.CodedHttpException;
import vexon.sellionpdv.common.exception.ResourceNotFoundException;
import vexon.sellionpdv.common.service.UsuarioContextService;
import vexon.sellionpdv.financeiro.dto.CancelamentoLancamentoRequestDTO;
import vexon.sellionpdv.financeiro.dto.LancamentoRequestDTO;
import vexon.sellionpdv.tenant.Tenant;
import vexon.sellionpdv.tenant.TenantContext;
import vexon.sellionpdv.usuario.Usuario;

@ExtendWith(MockitoExtension.class)
class LancamentoFinanceiroServiceTest {

    private static final Instant AGORA = Instant.parse("2026-08-12T12:00:00Z");

    @Mock private LancamentoFinanceiroRepository repository;
    @Mock private LancamentoFinanceiroPersistenciaService persistenciaService;
    @Mock private UsuarioContextService usuarioContextService;
    @Mock private Clock clock;
    @InjectMocks private LancamentoFinanceiroService service;

    private Usuario adminA;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(1L);
        adminA = Usuario.builder().id(10L).tenant(Tenant.builder().id(1L).build()).role("ROLE_ADMIN").build();
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void cancelamentoPreservaValoresOriginaisERegistraAtorMotivoEInstante() {
        when(clock.instant()).thenReturn(AGORA);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        LancamentoFinanceiro lancamento = lancamentoAtivo(7L, 1L);
        BigDecimal valorOriginal = lancamento.getValor();
        CategoriaLancamento categoriaOriginal = lancamento.getCategoria();
        LocalDate dataOriginal = lancamento.getDataReferencia();
        String descricaoOriginal = lancamento.getDescricao();
        when(usuarioContextService.getUsuarioAutenticado()).thenReturn(adminA);
        when(repository.findByIdAndTenantId(7L, 1L)).thenReturn(Optional.of(lancamento));
        when(repository.save(any())).thenAnswer(invocation -> invocation.<LancamentoFinanceiro>getArgument(0));

        service.cancelar(7L, new CancelamentoLancamentoRequestDTO("  Lançamento duplicado  "));

        assertEquals(StatusLancamentoFinanceiro.CANCELADO, lancamento.getStatus());
        assertEquals("Lançamento duplicado", lancamento.getMotivoCancelamento());
        assertEquals(AGORA, lancamento.getDataCancelamento().toInstant());
        assertEquals(adminA, lancamento.getUsuarioCancelamento());
        assertEquals(valorOriginal, lancamento.getValor());
        assertEquals(categoriaOriginal, lancamento.getCategoria());
        assertEquals(dataOriginal, lancamento.getDataReferencia());
        assertEquals(descricaoOriginal, lancamento.getDescricao());
        verify(repository).save(lancamento);
    }

    @Test
    void tenantDistintoNaoEncontraLancamentoNemPersisteCancelamento() {
        when(usuarioContextService.getUsuarioAutenticado()).thenReturn(adminA);
        when(repository.findByIdAndTenantId(8L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.cancelar(8L, new CancelamentoLancamentoRequestDTO("Tentativa válida")));

        verify(repository, never()).save(any());
    }

    @Test
    void atorDeOutroTenantEhRejeitadoAntesDaBusca() {
        Usuario adminB = Usuario.builder().id(20L).tenant(Tenant.builder().id(2L).build()).role("ROLE_ADMIN").build();
        when(usuarioContextService.getUsuarioAutenticado()).thenReturn(adminB);

        assertThrows(AccessDeniedException.class,
                () -> service.cancelar(7L, new CancelamentoLancamentoRequestDTO("Tentativa válida")));

        verify(repository, never()).findByIdAndTenantId(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void motivoInvalidoNaoAlteraLancamento() {
        LancamentoFinanceiro lancamento = lancamentoAtivo(7L, 1L);
        when(usuarioContextService.getUsuarioAutenticado()).thenReturn(adminA);
        when(repository.findByIdAndTenantId(7L, 1L)).thenReturn(Optional.of(lancamento));

        assertThrows(BusinessException.class,
                () -> service.cancelar(7L, new CancelamentoLancamentoRequestDTO("  x  ")));

        assertAtivoSemCancelamento(lancamento);
        verify(repository, never()).save(any());
    }

    @Test
    void lancamentoJaCanceladoNaoPodeSerEditado() {
        LancamentoFinanceiro lancamento = lancamentoAtivo(7L, 1L);
        lancamento.setStatus(StatusLancamentoFinanceiro.CANCELADO);
        when(repository.findByIdAndTenantId(7L, 1L)).thenReturn(Optional.of(lancamento));

        assertThrows(BusinessException.class, () -> service.atualizar(7L,
                new LancamentoRequestDTO("Alteração", new BigDecimal("10.00"),
                        CategoriaLancamento.ALUGUEL, LocalDate.of(2026, 8, 1))));

        verify(repository, never()).save(any());
    }

    @Test
    void primeiraChaveCriaLancamentoComHashEApenasUmEfeitoFinanceiro() {
        UUID chave = UUID.randomUUID();
        LancamentoRequestDTO dto = novoLancamentoRequest();
        when(repository.findByTenantIdAndIdempotencyKey(1L, chave)).thenReturn(Optional.empty());
        when(persistenciaService.persistir(any())).thenAnswer(invocation -> {
            LancamentoFinanceiro criado = invocation.<LancamentoFinanceiro>getArgument(0);
            criado.setId(71L);
            return criado;
        });

        var resultado = service.criar(dto, chave);

        assertEquals(false, resultado.replayed());
        assertEquals(71L, resultado.lancamento().id());
        verify(persistenciaService).persistir(any());
    }

    @Test
    void replayIdenticoRetornaOriginalSemNovoEfeitoFinanceiro() {
        UUID chave = UUID.randomUUID();
        LancamentoFinanceiro existente = lancamentoAtivo(71L, 1L);
        existente.setIdempotencyKey(chave);
        existente.setIdempotencyPayloadHash(hashDe(novoLancamentoRequest()));
        when(repository.findByTenantIdAndIdempotencyKey(1L, chave)).thenReturn(Optional.of(existente));

        var resultado = service.criar(novoLancamentoRequest(), chave);

        assertEquals(true, resultado.replayed());
        assertEquals(71L, resultado.lancamento().id());
        assertEquals(new BigDecimal("250.00"), existente.getValor());
        verify(persistenciaService, never()).persistir(any());
    }

    @Test
    void replayComValorDeEscalaDecimalEquivalenteNaoDuplicaEfeitoFinanceiro() {
        UUID chave = UUID.randomUUID();
        LancamentoFinanceiro existente = lancamentoAtivo(71L, 1L);
        existente.setIdempotencyKey(chave);
        existente.setIdempotencyPayloadHash(hashDe(novoLancamentoRequest()));
        when(repository.findByTenantIdAndIdempotencyKey(1L, chave)).thenReturn(Optional.of(existente));

        LancamentoRequestDTO valorComOutraEscala = new LancamentoRequestDTO("Aluguel", new BigDecimal("250.0"),
                CategoriaLancamento.ALUGUEL, LocalDate.of(2026, 8, 1));
        var resultado = service.criar(valorComOutraEscala, chave);

        assertEquals(true, resultado.replayed());
        assertEquals(new BigDecimal("250.00"), existente.getValor());
        verify(persistenciaService, never()).persistir(any());
    }

    @Test
    void mesmaChaveComOutroPayloadEhConflitoSemNovoEfeito() {
        UUID chave = UUID.randomUUID();
        LancamentoFinanceiro existente = lancamentoAtivo(71L, 1L);
        existente.setIdempotencyKey(chave);
        existente.setIdempotencyPayloadHash(hashDe(novoLancamentoRequest()));
        when(repository.findByTenantIdAndIdempotencyKey(1L, chave)).thenReturn(Optional.of(existente));

        LancamentoRequestDTO alterado = new LancamentoRequestDTO("Outro aluguel", new BigDecimal("250.00"),
                CategoriaLancamento.ALUGUEL, LocalDate.of(2026, 8, 1));
        CodedHttpException exception = assertThrows(CodedHttpException.class, () -> service.criar(alterado, chave));

        assertEquals("IDEMPOTENCY_KEY_REUSED", exception.getCode());
        verify(persistenciaService, never()).persistir(any());
    }

    @Test
    void colisaoConcorrenteRelidaRetornaReplaySemSegundoLancamento() {
        UUID chave = UUID.randomUUID();
        LancamentoFinanceiro existente = lancamentoAtivo(71L, 1L);
        existente.setIdempotencyKey(chave);
        existente.setIdempotencyPayloadHash(hashDe(novoLancamentoRequest()));
        when(repository.findByTenantIdAndIdempotencyKey(1L, chave))
                .thenReturn(Optional.empty(), Optional.of(existente));
        when(persistenciaService.persistir(any())).thenThrow(new DataIntegrityViolationException("unique"));

        var resultado = service.criar(novoLancamentoRequest(), chave);

        assertEquals(true, resultado.replayed());
        assertEquals(71L, resultado.lancamento().id());
        verify(repository, org.mockito.Mockito.times(2)).findByTenantIdAndIdempotencyKey(1L, chave);
        verify(persistenciaService).persistir(any());
    }

    @Test
    void mesmoUuidEmOutroTenantCriaOutroLancamentoSemConsultarTenantA() {
        UUID chave = UUID.randomUUID();
        TenantContext.setCurrentTenant(2L);
        when(repository.findByTenantIdAndIdempotencyKey(2L, chave)).thenReturn(Optional.empty());
        when(persistenciaService.persistir(any())).thenAnswer(invocation -> invocation.<LancamentoFinanceiro>getArgument(0));

        service.criar(novoLancamentoRequest(), chave);

        org.mockito.ArgumentCaptor<LancamentoFinanceiro> captor = org.mockito.ArgumentCaptor.forClass(LancamentoFinanceiro.class);
        verify(persistenciaService).persistir(captor.capture());
        assertEquals(2L, captor.getValue().getTenantId());
        verify(repository, never()).findByTenantIdAndIdempotencyKey(eq(1L), eq(chave));
    }

    private LancamentoFinanceiro lancamentoAtivo(Long id, Long tenantId) {
        return LancamentoFinanceiro.builder()
                .id(id).tenantId(tenantId).descricao("Aluguel")
                .valor(new BigDecimal("250.00")).categoria(CategoriaLancamento.ALUGUEL)
                .dataReferencia(LocalDate.of(2026, 8, 1)).status(StatusLancamentoFinanceiro.ATIVO).build();
    }

    private LancamentoRequestDTO novoLancamentoRequest() {
        return new LancamentoRequestDTO("Aluguel", new BigDecimal("250.00"), CategoriaLancamento.ALUGUEL,
                LocalDate.of(2026, 8, 1));
    }

    private String hashDe(LancamentoRequestDTO dto) {
        try {
            String representacao = String.join("\n", dto.descricao().trim(), dto.valor().stripTrailingZeros().toPlainString(),
                    dto.categoria().name(), dto.dataReferencia().toString());
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(representacao.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private void assertAtivoSemCancelamento(LancamentoFinanceiro lancamento) {
        assertEquals(StatusLancamentoFinanceiro.ATIVO, lancamento.getStatus());
        assertNull(lancamento.getMotivoCancelamento());
        assertNull(lancamento.getDataCancelamento());
        assertNull(lancamento.getUsuarioCancelamento());
    }
}
