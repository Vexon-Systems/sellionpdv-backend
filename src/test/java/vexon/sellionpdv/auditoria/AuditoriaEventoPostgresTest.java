package vexon.sellionpdv.auditoria;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class AuditoriaEventoPostgresTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("sellionpdv_auditoria_test")
            .withUsername("sellion_test")
            .withPassword("sellion_test");

    private Flyway flyway;

    @BeforeEach
    void prepararBanco() {
        flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
    }

    @AfterEach
    void limparBanco() {
        if (flyway != null) {
            flyway.clean();
        }
    }

    @Test
    void eventoAceitaInsercaoMasRejeitaUpdateEDelete() throws SQLException {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            long tenantId = inserirERetornarId(connection,
                    "INSERT INTO tenants (nome_fantasia) VALUES (?)", "Tenant de auditoria");
            long usuarioId = inserirUsuario(connection, tenantId);
            UUID eventoId = UUID.randomUUID();

            assertDoesNotThrow(() -> inserirEvento(connection, eventoId, tenantId, usuarioId));

            SQLException update = assertThrows(SQLException.class, () -> executarMutacao(connection,
                    "UPDATE eventos_auditoria SET motivo = 'alterado' WHERE id = ?", eventoId));
            assertEquals("55000", update.getSQLState());

            SQLException delete = assertThrows(SQLException.class, () -> executarMutacao(connection,
                    "DELETE FROM eventos_auditoria WHERE id = ?", eventoId));
            assertEquals("55000", delete.getSQLState());
        }
    }

    private long inserirUsuario(Connection connection, long tenantId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO usuarios (tenant_id, nome, email, senha_hash, \"role\") VALUES (?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, tenantId);
            statement.setString(2, "Auditor");
            statement.setString(3, UUID.randomUUID() + "@teste.invalid");
            statement.setString(4, "hash");
            statement.setString(5, "ROLE_ADMIN");
            statement.executeUpdate();
            try (var ids = statement.getGeneratedKeys()) {
                ids.next();
                return ids.getLong(1);
            }
        }
    }

    private long inserirERetornarId(Connection connection, String sql, String valor) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, valor);
            statement.executeUpdate();
            try (var ids = statement.getGeneratedKeys()) {
                ids.next();
                return ids.getLong(1);
            }
        }
    }

    private void inserirEvento(Connection connection, UUID eventoId, long tenantId, long usuarioId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO eventos_auditoria (
                    id, tenant_id, ator_usuario_id, acao, tipo_recurso, recurso_id, resultado, depois, ocorrido_em
                ) VALUES (?, ?, ?, 'CAIXA_ABERTO', 'CAIXA', 1, 'SUCESSO', '{}'::jsonb, now())
                """)) {
            statement.setObject(1, eventoId);
            statement.setLong(2, tenantId);
            statement.setLong(3, usuarioId);
            statement.executeUpdate();
        }
    }

    private void executarMutacao(Connection connection, String sql, UUID eventoId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, eventoId);
            statement.executeUpdate();
        }
    }
}
