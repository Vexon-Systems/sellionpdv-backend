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
import java.sql.Types;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
@DisplayName("SEL-SEC-004 — matriz de pagamento no PostgreSQL")
class VendaMatrizPagamentoPostgresTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("sellionpdv_payment_matrix_test")
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
    @DisplayName("V7 aceita somente as quatro combinações coerentes")
    void v7ProtegeNovasGravacoes() throws SQLException {
        flyway.migrate();

        try (Connection connection = abrirConexao()) {
            DadosMinimos dados = criarDadosMinimos(connection);

            assertDoesNotThrow(() -> inserirVenda(connection, dados, "DINHEIRO", null, null));
            assertDoesNotThrow(() -> inserirVenda(connection, dados, "PIX", null, null));
            assertDoesNotThrow(() -> inserirVenda(
                    connection, dados, "CREDITO", dados.maquininhaId(), "VISA"));
            assertDoesNotThrow(() -> inserirVenda(
                    connection, dados, "DEBITO", dados.maquininhaId(), "MASTERCARD"));

            assertViolacaoCheck(() -> inserirVenda(
                    connection, dados, "DINHEIRO", dados.maquininhaId(), null));
            assertViolacaoCheck(() -> inserirVenda(connection, dados, "PIX", null, "VISA"));
            assertViolacaoCheck(() -> inserirVenda(connection, dados, "CREDITO", null, "VISA"));
            assertViolacaoCheck(() -> inserirVenda(
                    connection, dados, "DEBITO", dados.maquininhaId(), null));
        }
    }

    @Test
    @DisplayName("forma de pagamento permanece NOT NULL")
    void formaPagamentoNulaEhRejeitada() throws SQLException {
        flyway.migrate();

        try (Connection connection = abrirConexao()) {
            DadosMinimos dados = criarDadosMinimos(connection);
            SQLException exception = assertThrows(SQLException.class,
                    () -> inserirVenda(connection, dados, null, null, null));
            assertEquals("23502", exception.getSQLState());
        }
    }

    @Test
    @DisplayName("V7 preserva histórico incoerente sem saneamento silencioso")
    void v7PreservaHistoricoIncoerente() throws SQLException {
        Flyway ateV6 = configurarFlyway("6");
        ateV6.migrate();

        long vendaId;
        try (Connection connection = abrirConexao()) {
            DadosMinimos dados = criarDadosMinimos(connection);
            vendaId = inserirVenda(
                    connection, dados, "DINHEIRO", dados.maquininhaId(), null);
        }

        assertDoesNotThrow(() -> configurarFlyway(null).migrate());

        try (Connection connection = abrirConexao();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT forma_pagamento, maquininha_id, bandeira_cartao FROM vendas WHERE id = ?")) {
            statement.setLong(1, vendaId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                assertEquals("DINHEIRO", result.getString(1));
                assertEquals(Long.class, result.getObject(2).getClass());
                assertEquals(null, result.getString(3));
            }
        }
    }

    @Test
    @DisplayName("UPDATE de histórico incoerente exige saneamento explícito")
    void updateHistoricoIncoerenteEhRejeitado() throws SQLException {
        Flyway ateV6 = configurarFlyway("6");
        ateV6.migrate();

        long vendaId;
        try (Connection connection = abrirConexao()) {
            DadosMinimos dados = criarDadosMinimos(connection);
            vendaId = inserirVenda(
                    connection, dados, "DINHEIRO", dados.maquininhaId(), null);
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

    private void assertViolacaoCheck(OperacaoSql operacao) {
        SQLException exception = assertThrows(SQLException.class, operacao::executar);
        assertEquals("23514", exception.getSQLState());
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
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
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
        long maquininhaId = inserirERetornarId(connection,
                "INSERT INTO maquininhas (tenant_id, nome, marca, taxa_debito, taxa_credito) "
                        + "VALUES (?, ?, ?, ?, ?)",
                statement -> {
                    statement.setLong(1, tenantId);
                    statement.setString(2, "Terminal");
                    statement.setString(3, "Teste");
                    statement.setBigDecimal(4, BigDecimal.ZERO);
                    statement.setBigDecimal(5, BigDecimal.ZERO);
                });
        return new DadosMinimos(tenantId, usuarioId, caixaId, maquininhaId);
    }

    private long inserirVenda(
            Connection connection,
            DadosMinimos dados,
            String formaPagamento,
            Long maquininhaId,
            String bandeira
    ) throws SQLException {
        return inserirERetornarId(connection,
                "INSERT INTO vendas (tenant_id, caixa_id, forma_pagamento, maquininha_id, bandeira_cartao, "
                        + "subtotal, desconto_aplicado, motivo_desconto, total_final, idempotency_key, usuario_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                statement -> {
                    statement.setLong(1, dados.tenantId());
                    statement.setLong(2, dados.caixaId());
                    if (formaPagamento == null) {
                        statement.setNull(3, Types.VARCHAR);
                    } else {
                        statement.setString(3, formaPagamento);
                    }
                    if (maquininhaId == null) {
                        statement.setNull(4, Types.BIGINT);
                    } else {
                        statement.setLong(4, maquininhaId);
                    }
                    if (bandeira == null) {
                        statement.setNull(5, Types.VARCHAR);
                    } else {
                        statement.setString(5, bandeira);
                    }
                    statement.setBigDecimal(6, new BigDecimal("10.00"));
                    statement.setBigDecimal(7, BigDecimal.ZERO);
                    statement.setNull(8, Types.VARCHAR);
                    statement.setBigDecimal(9, new BigDecimal("10.00"));
                    statement.setObject(10, UUID.randomUUID());
                    statement.setLong(11, dados.usuarioId());
                });
    }

    private long inserirERetornarId(
            Connection connection,
            String sql,
            ConfiguradorStatement configurador
    ) throws SQLException {
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

    @FunctionalInterface
    private interface OperacaoSql {
        void executar() throws SQLException;
    }

    private record DadosMinimos(long tenantId, long usuarioId, long caixaId, long maquininhaId) {
    }
}
