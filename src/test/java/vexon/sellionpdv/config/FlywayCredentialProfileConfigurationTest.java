package vexon.sellionpdv.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class FlywayCredentialProfileConfigurationTest {

    @Test
    void deveUsarCredenciaisDeMigrationNosPerfisDeDeploy() throws IOException {
        assertMigrationCredentials("application-prod.properties");
        assertMigrationCredentials("application-staging.properties");
    }

    private void assertMigrationCredentials(String resourceName) throws IOException {
        Properties properties = new Properties();

        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new IOException("Recurso de configuração não encontrado: " + resourceName);
            }
            properties.load(input);
        }

        assertEquals("${DB_MIGRATION_USERNAME}", properties.getProperty("spring.flyway.user"));
        assertEquals("${DB_MIGRATION_PASSWORD}", properties.getProperty("spring.flyway.password"));
    }
}
