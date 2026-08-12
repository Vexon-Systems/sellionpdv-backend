package vexon.sellionpdv.financeiro;

import org.flywaydb.core.Flyway;
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
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
@DisplayName("SEL-SEC-010 — cancelamento de lançamento financeiro no PostgreSQL")
class LancamentoFinanceiroCancelamentoPostgresTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("sellionpdv_financeiro_test")
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
    @DisplayName("V11 preserva lançamento histórico como ativo e exige dados completos no cancelamento")
    void migrationPreservaHistoricoEExigeDadosDeCancelamento() throws SQLException {
        configurarFlyway("10").migrate();

        long lancamentoId;
        long usuarioId;
        try (Connection connection = abrirConexao()) {
            DadosTenant dados = criarTenantComUsuario(connection, "Tenant A");
            usuarioId = dados.usuarioId();
            lancamentoId = inserirLancamento(connection, dados.tenantId(), "Aluguel", new BigDecimal("2500.00"));
        }

        assertDoesNotThrow(() -> configurarFlyway(null).migrate());

        try (Connection connection = abrirConexao()) {
            try (PreparedStatement consulta = connection.prepareStatement(
                    "SELECT status, motivo_cancelamento, data_cancelamento, usuario_cancelamento_id "
                            + "FROM lancamentos_financeiros WHERE id = ?")) {
                consulta.setLong(1, lancamentoId);
                try (ResultSet resultado = consulta.executeQuery()) {
                    resultado.next();
                    assertEquals("ATIVO", resultado.getString("status"));
                    assertNull(resultado.getString("motivo_cancelamento"));
                    assertNull(resultado.getObject("data_cancelamento"));
                    assertNull(resultado.getObject("usuario_cancelamento_id"));
                }
            }

            try (PreparedStatement incompleto = connection.prepareStatement(
                    "UPDATE lancamentos_financeiros SET status = 'CANCELADO' WHERE id = ?")) {
                incompleto.setLong(1, lancamentoId);
                SQLException exception = assertThrows(SQLException.class, incompleto::executeUpdate);
                assertEquals("23514", exception.getSQLState());
            }

            try (PreparedStatement cancelar = connection.prepareStatement(
                    "UPDATE lancamentos_financeiros SET status = 'CANCELADO', motivo_cancelamento = ?, "
                            + "data_cancelamento = ?, usuario_cancelamento_id = ? WHERE id = ?")) {
                cancelar.setString(1, "Despesa duplicada");
                cancelar.setObject(2, OffsetDateTime.now());
                cancelar.setLong(3, usuarioId);
                cancelar.setLong(4, lancamentoId);
                assertEquals(1, cancelar.executeUpdate());
            }
        }
    }

    @Test
    @DisplayName("banco rejeita usuário de cancelamento pertencente a outro tenant")
    void bancoRejeitaUsuarioCancelamentoDeOutroTenant() throws SQLException {
        flyway.migrate();

        try (Connection connection = abrirConexao()) {
            DadosTenant tenantA = criarTenantComUsuario(connection, "Tenant A");
            DadosTenant tenantB = criarTenantComUsuario(connection, "Tenant B");
            long lancamentoId = inserirLancamento(connection, tenantA.tenantId(), "Energia", new BigDecimal("450.00"));

            try (PreparedStatement cancelar = connection.prepareStatement(
                    "UPDATE lancamentos_financeiros SET status = 'CANCELADO', motivo_cancelamento = ?, "
                            + "data_cancelamento = ?, usuario_cancelamento_id = ? WHERE id = ?")) {
                cancelar.setString(1, "Despesa lançada em duplicidade");
                cancelar.setObject(2, OffsetDateTime.now());
                cancelar.setLong(3, tenantB.usuarioId());
                cancelar.setLong(4, lancamentoId);
                SQLException exception = assertThrows(SQLException.class, cancelar::executeUpdate);
                assertEquals("23503", exception.getSQLState());
            }

            try (PreparedStatement consulta = connection.prepareStatement(
                    "SELECT status FROM lancamentos_financeiros WHERE id = ?")) {
                consulta.setLong(1, lancamentoId);
                try (ResultSet resultado = consulta.executeQuery()) {
                    resultado.next();
                    assertEquals("ATIVO", resultado.getString(1));
                }
            }
        }
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

    private DadosTenant criarTenantComUsuario(Connection connection, String nomeTenant) throws SQLException {
        long tenantId = inserirERetornarId(connection,
                "INSERT INTO tenants (nome_fantasia) VALUES (?)",
                statement -> statement.setString(1, nomeTenant));
        long usuarioId = inserirERetornarId(connection,
                "INSERT INTO usuarios (tenant_id, nome, email, senha_hash, \"role\") VALUES (?, ?, ?, ?, ?)",
                statement -> {
                    statement.setLong(1, tenantId);
                    statement.setString(2, "Administrador");
                    statement.setString(3, UUID.randomUUID() + "@teste.invalid");
                    statement.setString(4, "hash");
                    statement.setString(5, "ROLE_ADMIN");
                });
        return new DadosTenant(tenantId, usuarioId);
    }

    private long inserirLancamento(Connection connection, long tenantId, String descricao, BigDecimal valor)
            throws SQLException {
        return inserirERetornarId(connection,
                "INSERT INTO lancamentos_financeiros (tenant_id, descricao, valor, categoria, data_referencia) "
                        + "VALUES (?, ?, ?, ?, CURRENT_DATE)",
                statement -> {
                    statement.setLong(1, tenantId);
                    statement.setString(2, descricao);
                    statement.setBigDecimal(3, valor);
                    statement.setString(4, "OUTROS");
                });
    }

    private long inserirERetornarId(Connection connection, String sql, ConfiguradorStatement configurador)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            configurador.configurar(statement);
            statement.executeUpdate();
            try (ResultSet ids = statement.getGeneratedKeys()) {
                assertTrue(ids.next(), "Identificador não retornado.");
                return ids.getLong(1);
            }
        }
    }

    @FunctionalInterface
    private interface ConfiguradorStatement {
        void configurar(PreparedStatement statement) throws SQLException;
    }

    private record DadosTenant(long tenantId, long usuarioId) {
    }
}
