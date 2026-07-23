package vexon.sellionpdv.venda;

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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
@DisplayName("SEL-SEC-003 — coerência de desconto e motivo no PostgreSQL")
class VendaDescontoPostgresTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("sellionpdv_discount_test")
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
    @DisplayName("novas vendas respeitam a coerência desconto e motivo")
    void novasVendasRespeitamConstraint() throws SQLException {
        flyway.migrate();

        try (Connection connection = abrirConexao()) {
            DadosMinimos dados = criarDadosMinimos(connection);

            assertDoesNotThrow(() -> inserirVenda(connection, dados, new BigDecimal("10.00"), "Autorizado"));

            SQLException semMotivo = assertThrows(SQLException.class,
                    () -> inserirVenda(connection, dados, new BigDecimal("10.00"), null));
            assertEquals("23514", semMotivo.getSQLState());

            SQLException motivoSemDesconto = assertThrows(SQLException.class,
                    () -> inserirVenda(connection, dados, BigDecimal.ZERO, "Ambíguo"));
            assertEquals("23514", motivoSemDesconto.getSQLState());
        }
    }

    @Test
    @DisplayName("V6 não bloqueia nem altera venda histórica sem motivo")
    void migrationPreservaHistoricoInconsistente() throws SQLException {
        Flyway ateV5 = configurarFlyway("5");
        ateV5.migrate();

        long vendaId;
        try (Connection connection = abrirConexao()) {
            DadosMinimos dados = criarDadosMinimos(connection);
            vendaId = inserirVendaAntesDaV6(connection, dados, new BigDecimal("10.00"));
        }

        assertDoesNotThrow(() -> configurarFlyway(null).migrate());

        try (Connection connection = abrirConexao();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT motivo_desconto FROM vendas WHERE id = ?")) {
            statement.setLong(1, vendaId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                assertNull(result.getString(1));
            }
        }
    }

    @Test
    @DisplayName("atualização de histórico inconsistente exige saneamento explícito")
    void updateHistoricoInconsistenteEhRejeitado() throws SQLException {
        Flyway ateV5 = configurarFlyway("5");
        ateV5.migrate();

        long vendaId;
        try (Connection connection = abrirConexao()) {
            DadosMinimos dados = criarDadosMinimos(connection);
            vendaId = inserirVendaAntesDaV6(connection, dados, new BigDecimal("10.00"));
        }
        configurarFlyway(null).migrate();

        try (Connection connection = abrirConexao();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE vendas SET status = status WHERE id = ?")) {
            statement.setLong(1, vendaId);
            SQLException exception = assertThrows(SQLException.class, statement::executeUpdate);
            assertEquals("23514", exception.getSQLState());
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

    private DadosMinimos criarDadosMinimos(Connection connection) throws SQLException {
        long tenantId = inserirERetornarId(connection,
                "INSERT INTO tenants (nome_fantasia) VALUES (?)",
                statement -> statement.setString(1, "Tenant de teste"));
        long usuarioId = inserirERetornarId(connection,
                "INSERT INTO usuarios (tenant_id, nome, email, senha_hash, \"role\") VALUES (?, ?, ?, ?, ?)",
                statement -> {
                    statement.setLong(1, tenantId);
                    statement.setString(2, "Administrador");
                    statement.setString(3, UUID.randomUUID() + "@teste.invalid");
                    statement.setString(4, "hash");
                    statement.setString(5, "ROLE_ADMIN");
                });
        long caixaId = inserirERetornarId(connection,
                "INSERT INTO caixas (tenant_id, status, saldo_inicial, usuario_abertura_id) VALUES (?, ?, ?, ?)",
                statement -> {
                    statement.setLong(1, tenantId);
                    statement.setString(2, "ABERTO");
                    statement.setBigDecimal(3, BigDecimal.ZERO);
                    statement.setLong(4, usuarioId);
                });
        return new DadosMinimos(tenantId, usuarioId, caixaId);
    }

    private long inserirVenda(Connection connection, DadosMinimos dados, BigDecimal desconto, String motivo)
            throws SQLException {
        return inserirERetornarId(connection,
                "INSERT INTO vendas (tenant_id, caixa_id, forma_pagamento, subtotal, desconto_aplicado, "
                        + "motivo_desconto, total_final, idempotency_key, usuario_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                statement -> preencherVenda(statement, dados, desconto, motivo, true));
    }

    private long inserirVendaAntesDaV6(Connection connection, DadosMinimos dados, BigDecimal desconto)
            throws SQLException {
        return inserirERetornarId(connection,
                "INSERT INTO vendas (tenant_id, caixa_id, forma_pagamento, subtotal, desconto_aplicado, "
                        + "total_final, idempotency_key, usuario_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                statement -> preencherVenda(statement, dados, desconto, null, false));
    }

    private void preencherVenda(
            PreparedStatement statement,
            DadosMinimos dados,
            BigDecimal desconto,
            String motivo,
            boolean incluiMotivo
    ) throws SQLException {
        statement.setLong(1, dados.tenantId());
        statement.setLong(2, dados.caixaId());
        statement.setString(3, "DINHEIRO");
        statement.setBigDecimal(4, new BigDecimal("100.00"));
        statement.setBigDecimal(5, desconto);
        int indice = 6;
        if (incluiMotivo) {
            statement.setString(indice++, motivo);
        }
        statement.setBigDecimal(indice++, new BigDecimal("100.00").subtract(desconto));
        statement.setObject(indice++, UUID.randomUUID());
        statement.setLong(indice, dados.usuarioId());
    }

    private long inserirERetornarId(Connection connection, String sql, ConfiguradorStatement configurador)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            configurador.configurar(statement);
            statement.executeUpdate();
            try (ResultSet ids = statement.getGeneratedKeys()) {
                if (!ids.next()) {
                    throw new SQLException("Identificador não retornado.");
                }
                return ids.getLong(1);
            }
        }
    }

    @FunctionalInterface
    private interface ConfiguradorStatement {
        void configurar(PreparedStatement statement) throws SQLException;
    }

    private record DadosMinimos(long tenantId, long usuarioId, long caixaId) {
    }
}
