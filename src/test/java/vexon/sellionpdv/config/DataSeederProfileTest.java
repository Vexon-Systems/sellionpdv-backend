package vexon.sellionpdv.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regressão do SAST-02 (credenciais padrão semeadas sem controle de ambiente):
 * confirma que o bean DataSeeder — que cria admin@sellion.com.br — só existe nos
 * profiles dev/test/local, nunca em prod/staging.
 */
@DisplayName("DataSeeder — restrição de profile (SAST-02)")
class DataSeederProfileTest {

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
    @ActiveProfiles("prod")
    @DisplayName("Profile prod")
    class ComProfileProd {

        @Autowired
        private ApplicationContext context;

        @Test
        @DisplayName("DataSeeder não deve existir como bean")
        void dataSeederNaoDeveExistirEmProd() {
            assertEquals(0, context.getBeanNamesForType(DataSeeder.class).length);
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
    @ActiveProfiles("staging")
    @DisplayName("Profile staging")
    class ComProfileStaging {

        @Autowired
        private ApplicationContext context;

        @Test
        @DisplayName("DataSeeder não deve existir como bean")
        void dataSeederNaoDeveExistirEmStaging() {
            assertEquals(0, context.getBeanNamesForType(DataSeeder.class).length);
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
    @ActiveProfiles("dev")
    @DisplayName("Profile dev")
    class ComProfileDev {

        @Autowired
        private ApplicationContext context;

        @Test
        @DisplayName("DataSeeder deve existir como bean (sanity check — a lista de profiles inclui 'dev')")
        void dataSeederDeveExistirEmDev() {
            assertEquals(1, context.getBeanNamesForType(DataSeeder.class).length);
        }
    }
}
