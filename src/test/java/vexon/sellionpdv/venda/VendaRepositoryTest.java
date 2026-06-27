package vexon.sellionpdv.venda;

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
import vexon.sellionpdv.caixa.Caixa;
import vexon.sellionpdv.caixa.CaixaRepository;
import vexon.sellionpdv.caixa.StatusCaixa;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hibernate 6 captura o tenant no momento em que a Session é aberta — antes do @BeforeEach.
 * Por isso, usamos @BeforeTransaction para comitar o Tenant e definir o TenantContext
 * ANTES que a transação de teste abra a Session, garantindo que @TenantId seja resolvido
 * corretamente e que a FK caixas.tenant_id → tenants.id seja satisfeita.
 *
 * Nota: @Nested é incompatível com este padrão — o Spring's TransactionalTestExecutionListener
 * não propaga @BeforeTransaction para classes aninhadas, deixando savedTenant nulo no @BeforeEach.
 * Os testes ficam flat intencionalmente.
 *
 * Isolamento entre tenants não é testável neste setup: mudar TenantContext mid-transação
 * não altera o tenant já capturado pela Session do Hibernate 6. A garantia de isolamento
 * é do ORM e está documentada no ADR de multi-tenancy.
 */
@DataJpaTest
@Import(TenantIdentifierResolver.class)
@DisplayName("VendaRepository")
class VendaRepositoryTest {

    @Autowired private VendaRepository vendaRepository;
    @Autowired private CaixaRepository caixaRepository;
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

    private Venda salvarVenda(UUID idempotencyKey) {
        return vendaRepository.saveAndFlush(
                Venda.builder()
                        .caixa(caixa)
                        .usuario(operador)
                        .status(StatusVenda.CONCLUIDA)
                        .formaPagamento(FormaPagamento.DINHEIRO)
                        .subtotal(new BigDecimal("30.00"))
                        .descontoAplicado(BigDecimal.ZERO)
                        .totalFinal(new BigDecimal("30.00"))
                        .idempotencyKey(idempotencyKey)
                        .dataVenda(OffsetDateTime.now())
                        .build()
        );
    }

    @Test
    @DisplayName("Deve persistir a venda quando os dados são válidos")
    void deve_PersistirVenda_quando_DadosValidos() {
        UUID key = UUID.randomUUID();
        Venda salva = salvarVenda(key);
        entityManager.clear(); // evita cache de primeiro nível — força reload real do BD

        Venda encontrada = vendaRepository.findById(salva.getId()).orElseThrow();

        assertNotNull(encontrada.getId());
        assertEquals(StatusVenda.CONCLUIDA, encontrada.getStatus());
        assertEquals(FormaPagamento.DINHEIRO, encontrada.getFormaPagamento());
        assertEquals(0, new BigDecimal("30.00").compareTo(encontrada.getTotalFinal()));
        assertEquals(key, encontrada.getIdempotencyKey());
    }

    @Test
    @DisplayName("Deve retornar as vendas quando buscar por caixa")
    void deve_RetornarVendas_quando_BuscarPorCaixa() {
        salvarVenda(UUID.randomUUID());
        salvarVenda(UUID.randomUUID());

        List<Venda> vendas = vendaRepository.findByCaixa(caixa);

        assertEquals(2, vendas.size());
    }

    @Test
    @DisplayName("Deve retornar lista vazia quando não há vendas no caixa")
    void deve_RetornarListaVazia_quando_CaixaSemVendas() {
        List<Venda> vendas = vendaRepository.findByCaixa(caixa);

        assertTrue(vendas.isEmpty());
    }

    @Test
    @DisplayName("Deve retornar a venda existente quando buscar por chave de idempotência")
    void deve_RetornarVendaExistente_quando_BuscarPorIdempotencyKey() {
        UUID key = UUID.randomUUID();
        Venda salva = salvarVenda(key);

        Optional<Venda> resultado = vendaRepository.findByIdempotencyKey(key);

        assertTrue(resultado.isPresent());
        assertEquals(salva.getId(), resultado.get().getId());
        assertEquals(key, resultado.get().getIdempotencyKey());
    }

    @Test
    @DisplayName("Deve retornar Optional vazio quando a chave não existir")
    void deve_RetornarOptionalVazio_quando_ChaveNaoExistir() {
        Optional<Venda> resultado = vendaRepository.findByIdempotencyKey(UUID.randomUUID());

        assertTrue(resultado.isEmpty());
    }
}
