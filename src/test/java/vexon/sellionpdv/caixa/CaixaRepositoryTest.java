package vexon.sellionpdv.caixa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;
import vexon.sellionpdv.tenant.Tenant;
import vexon.sellionpdv.tenant.TenantContext;
import vexon.sellionpdv.tenant.TenantIdentifierResolver;
import vexon.sellionpdv.tenant.TenantRepository;
import vexon.sellionpdv.usuario.Usuario;
import vexon.sellionpdv.usuario.UsuarioRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hibernate 6 captura o tenant no momento em que a Session é aberta — antes do @BeforeEach.
 * Por isso, usamos @BeforeTransaction para comitar o Tenant e definir o TenantContext
 * ANTES que a transação de teste abra a Session, garantindo que @TenantId seja resolvido
 * corretamente e que a FK caixas.tenant_id → tenants.id seja satisfeita.
 */
@DataJpaTest
@Import(TenantIdentifierResolver.class)
@DisplayName("CaixaRepository")
class CaixaRepositoryTest {

    @Autowired private CaixaRepository caixaRepository;
    @Autowired private MovimentacaoCaixaRepository movimentacaoRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @PersistenceContext private EntityManager entityManager;

    private Tenant savedTenant;
    private Usuario operador;
    private Caixa caixa;

    @BeforeTransaction
    void setUpTenant() {
        savedTenant = tenantRepository.save(
                Tenant.builder().nomeFantasia("Franquia Test").build()
        );
        TenantContext.setCurrentTenant(savedTenant.getId());
    }

    @AfterTransaction
    void tearDownTenant() {
        tenantRepository.deleteById(savedTenant.getId());
        TenantContext.clear();
    }

    @BeforeEach
    void setUp() {
        Tenant tenantRef = entityManager.find(Tenant.class, savedTenant.getId());

        operador = usuarioRepository.saveAndFlush(
                Usuario.builder()
                        .nome("Operador").email("op@test.com")
                        .senhaHash("hash").role("ROLE_ADMIN")
                        .tenant(tenantRef)
                        .build()
        );

        caixa = caixaRepository.saveAndFlush(
                Caixa.builder()
                        .status(StatusCaixa.ABERTO)
                        .saldoInicial(new BigDecimal("100.00"))
                        .dataAbertura(OffsetDateTime.now())
                        .operadorAbertura(operador)
                        .build()
        );
    }

    @Test
    @DisplayName("CR1 — deve_RetornarCaixa_quando_BuscarPorStatusABERTO")
    void deve_RetornarCaixa_quando_BuscarPorStatusABERTO() {
        entityManager.clear();

        Optional<Caixa> resultado = caixaRepository.findByStatus(StatusCaixa.ABERTO);

        assertTrue(resultado.isPresent());
        assertEquals(caixa.getId(), resultado.get().getId());
        assertEquals(StatusCaixa.ABERTO, resultado.get().getStatus());
        assertEquals(0, new BigDecimal("100.00").compareTo(resultado.get().getSaldoInicial()));
    }

    @Test
    @DisplayName("CR2 — deve_RetornarOptionalVazio_quando_SoCaixaFechadoExiste")
    void deve_RetornarOptionalVazio_quando_SoCaixaFechadoExiste() {
        caixa.setStatus(StatusCaixa.FECHADO);
        caixaRepository.saveAndFlush(caixa);
        entityManager.clear();

        Optional<Caixa> resultado = caixaRepository.findByStatus(StatusCaixa.ABERTO);

        assertTrue(resultado.isEmpty());
    }

    @Test
    @DisplayName("CR3 — deve_RetornarMovimentacoesDoCaixa_quando_BuscarPorCaixa")
    void deve_RetornarMovimentacoesDoCaixa_quando_BuscarPorCaixa() {
        movimentacaoRepository.saveAndFlush(
                MovimentacaoCaixa.builder()
                        .caixa(caixa).tipo(TipoMovimentacaoCaixa.SANGRIA)
                        .valor(new BigDecimal("50.00")).motivo("Sangria teste")
                        .dataMovimentacao(OffsetDateTime.now())
                        .build()
        );
        movimentacaoRepository.saveAndFlush(
                MovimentacaoCaixa.builder()
                        .caixa(caixa).tipo(TipoMovimentacaoCaixa.REFORCO)
                        .valor(new BigDecimal("30.00")).motivo("Reforço teste")
                        .dataMovimentacao(OffsetDateTime.now())
                        .build()
        );

        List<MovimentacaoCaixa> resultado = movimentacaoRepository.findByCaixa(caixa);

        assertEquals(2, resultado.size());
    }

    @Test
    @DisplayName("CR4 — deve_RetornarListaVazia_quando_CaixaSemMovimentacoes")
    void deve_RetornarListaVazia_quando_CaixaSemMovimentacoes() {
        List<MovimentacaoCaixa> resultado = movimentacaoRepository.findByCaixa(caixa);

        assertTrue(resultado.isEmpty());
    }
}
