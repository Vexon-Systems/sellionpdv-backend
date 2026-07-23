package vexon.sellionpdv.venda;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valida SEL-SEC-001 em PostgreSQL real. O container é descartável e não usa
 * nenhuma conexão, dado ou credencial de produção.
 */
@Testcontainers
@DisplayName("SEL-SEC-001 — isolamento de maquininha entre tenants no PostgreSQL")
class VendaMaquininhaCrossTenantPostgresTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("sellionpdv_security_test")
            .withUsername("sellion_test")
            .withPassword("sellion_test");

    private Flyway flyway;

    @BeforeEach
    void prepararBancoLimpo() {
        flyway = configurarFlyway(null);
        flyway.clean();
    }

    @AfterEach
    void limparBanco() {
        if (flyway != null) {
            flyway.clean();
        }
    }

    @Test
    @DisplayName("aceita venda quando maquininha e venda pertencem ao mesmo tenant")
    void deve_AceitarVenda_quando_MaquininhaPertenceAoMesmoTenant() throws SQLException {
        flyway.migrate();

        try (Connection connection = abrirConexao()) {
            DadosMinimos dados = criarDadosMinimos(connection);

            assertDoesNotThrow(() -> inserirVenda(
                    connection,
                    dados.tenantAId(),
                    dados.caixaAId(),
                    dados.usuarioAId(),
                    dados.maquininhaAId()
            ));
        }
    }

    @Test
    @DisplayName("rejeita venda do tenant A que referencia maquininha do tenant B")
    void deve_RejeitarVenda_quando_MaquininhaPertenceAOutroTenant() throws SQLException {
        flyway.migrate();

        try (Connection connection = abrirConexao()) {
            DadosMinimos dados = criarDadosMinimos(connection);

            SQLException exception = assertThrows(SQLException.class, () -> inserirVenda(
                    connection,
                    dados.tenantAId(),
                    dados.caixaAId(),
                    dados.usuarioAId(),
                    dados.maquininhaBId()
            ));

            assertEquals("23503", exception.getSQLState(),
                    "A FK composta deve rejeitar o vínculo entre tenants diferentes.");
        }
    }

    @Test
    @DisplayName("interrompe V5 sem alterar dados quando encontra contaminação histórica")
    void deve_InterromperMigrationV5_quando_ExisteVendaComMaquininhaDeOutroTenant() throws SQLException {
        Flyway flywayAteV4 = configurarFlyway("4");
        flywayAteV4.migrate();

        try (Connection connection = abrirConexao()) {
            DadosMinimos dados = criarDadosMinimos(connection);
            inserirVenda(
                    connection,
                    dados.tenantAId(),
                    dados.caixaAId(),
                    dados.usuarioAId(),
                    dados.maquininhaBId()
            );
        }

        FlywayException exception = assertThrows(FlywayException.class,
                () -> configurarFlyway(null).migrate());

        assertTrue(contemMensagem(exception, "SEL-SEC-001: existem vendas com maquininha ausente ou pertencente a outro tenant"));
    }

    private Flyway configurarFlyway(String target) {
        var configuracao = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false);

        if (target != null) {
            configuracao.target(target);
        }

        return configuracao.load();
    }

    private Connection abrirConexao() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private DadosMinimos criarDadosMinimos(Connection connection) throws SQLException {
        long tenantAId = inserirTenant(connection, "Tenant A");
        long tenantBId = inserirTenant(connection, "Tenant B");
        long usuarioAId = inserirUsuario(connection, tenantAId, "operador-a@teste.invalid");
        long caixaAId = inserirCaixa(connection, tenantAId, usuarioAId);
        long maquininhaAId = inserirMaquininha(connection, tenantAId, "Terminal A");
        long maquininhaBId = inserirMaquininha(connection, tenantBId, "Terminal B");

        return new DadosMinimos(tenantAId, usuarioAId, caixaAId, maquininhaAId, maquininhaBId);
    }

    private long inserirTenant(Connection connection, String nome) throws SQLException {
        return inserirERetornarId(connection,
                "INSERT INTO tenants (nome_fantasia) VALUES (?)",
                statement -> statement.setString(1, nome));
    }

    private long inserirUsuario(Connection connection, long tenantId, String email) throws SQLException {
        return inserirERetornarId(connection,
                "INSERT INTO usuarios (tenant_id, nome, email, senha_hash, \"role\") VALUES (?, ?, ?, ?, ?)",
                statement -> {
                    statement.setLong(1, tenantId);
                    statement.setString(2, "Operador de teste");
                    statement.setString(3, email);
                    statement.setString(4, "hash-de-teste");
                    statement.setString(5, "ROLE_OPERADOR");
                });
    }

    private long inserirCaixa(Connection connection, long tenantId, long usuarioId) throws SQLException {
        return inserirERetornarId(connection,
                "INSERT INTO caixas (tenant_id, status, saldo_inicial, usuario_abertura_id) VALUES (?, ?, ?, ?)",
                statement -> {
                    statement.setLong(1, tenantId);
                    statement.setString(2, "ABERTO");
                    statement.setBigDecimal(3, BigDecimal.ZERO);
                    statement.setLong(4, usuarioId);
                });
    }

    private long inserirMaquininha(Connection connection, long tenantId, String nome) throws SQLException {
        return inserirERetornarId(connection,
                "INSERT INTO maquininhas (tenant_id, nome, marca, taxa_debito, taxa_credito) VALUES (?, ?, ?, ?, ?)",
                statement -> {
                    statement.setLong(1, tenantId);
                    statement.setString(2, nome);
                    statement.setString(3, "Teste");
                    statement.setBigDecimal(4, BigDecimal.ZERO);
                    statement.setBigDecimal(5, BigDecimal.ZERO);
                });
    }

    private void inserirVenda(Connection connection, long tenantId, long caixaId, long usuarioId, long maquininhaId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO vendas (tenant_id, caixa_id, forma_pagamento, maquininha_id, subtotal, desconto_aplicado, "
                        + "total_final, idempotency_key, usuario_id, bandeira_cartao) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setLong(1, tenantId);
            statement.setLong(2, caixaId);
            statement.setString(3, "CREDITO");
            statement.setLong(4, maquininhaId);
            statement.setBigDecimal(5, new BigDecimal("10.00"));
            statement.setBigDecimal(6, BigDecimal.ZERO);
            statement.setBigDecimal(7, new BigDecimal("10.00"));
            statement.setObject(8, UUID.randomUUID());
            statement.setLong(9, usuarioId);
            statement.setString(10, "VISA");
            statement.executeUpdate();
        }
    }

    private long inserirERetornarId(Connection connection, String sql, ConfiguradorStatement configurador) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            configurador.configurar(statement);
            statement.executeUpdate();

            try (ResultSet ids = statement.getGeneratedKeys()) {
                if (!ids.next()) {
                    throw new SQLException("O banco não retornou o identificador gerado.");
                }
                return ids.getLong(1);
            }
        }
    }

    private boolean contemMensagem(Throwable throwable, String esperada) {
        Throwable atual = throwable;
        while (atual != null) {
            if (atual.getMessage() != null && atual.getMessage().contains(esperada)) {
                return true;
            }
            atual = atual.getCause();
        }
        return false;
    }

    @FunctionalInterface
    private interface ConfiguradorStatement {
        void configurar(PreparedStatement statement) throws SQLException;
    }

    private record DadosMinimos(
            long tenantAId,
            long usuarioAId,
            long caixaAId,
            long maquininhaAId,
            long maquininhaBId
    ) {
    }
}
