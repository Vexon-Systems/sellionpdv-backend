package vexon.sellionpdv.funcionario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regressão do SAST-01 (escalonamento de privilégio): confirma que
 * {@code @PreAuthorize("hasRole('ADMIN')")} em FuncionarioController realmente bloqueia
 * um usuário OPERADOR.
 *
 * Diferente dos demais *ControllerTest deste projeto (MockMvc standalone, sem Spring
 * Security — ver comentário em RelatorioControllerTest), este teste sobe o contexto
 * Spring completo com @AutoConfigureMockMvc para que a cadeia de segurança real
 * (SecurityFilterChain + @PreAuthorize) seja exercitada. É o único jeito de provar que a
 * autorização em si funciona, não só a lógica do Service.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("FuncionarioController — autorização (SAST-01)")
class FuncionarioAutorizacaoTest {

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("Usuário autenticado como OPERADOR")
    class ComoOperador {

        @Test
        @WithMockUser(roles = "OPERADOR")
        @DisplayName("Deve receber 403 ao tentar LISTAR funcionários")
        void deve_Retornar403_aoListar() throws Exception {
            mockMvc.perform(get("/api/funcionarios"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "OPERADOR")
        @DisplayName("Deve receber 403 ao tentar CRIAR um funcionário (inclusive pedindo role ADMIN)")
        void deve_Retornar403_aoCriar() throws Exception {
            String corpo = """
                    {"nome": "Invasor", "email": "invasor@sellion.com.br", "senha": "SenhaQualquer123", "role": "ADMIN"}
                    """;

            mockMvc.perform(post("/api/funcionarios")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "OPERADOR")
        @DisplayName("Deve receber 403 ao tentar ATUALIZAR — cenário exato do SAST-01 (auto-promoção via PUT)")
        void deve_Retornar403_aoAtualizar() throws Exception {
            String corpo = """
                    {"nome": "Qualquer Nome", "role": "ADMIN"}
                    """;

            mockMvc.perform(put("/api/funcionarios/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "OPERADOR")
        @DisplayName("Deve receber 403 ao tentar INATIVAR um funcionário")
        void deve_Retornar403_aoInativar() throws Exception {
            mockMvc.perform(delete("/api/funcionarios/1"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Sem autenticação")
    class SemAutenticacao {

        @Test
        @DisplayName("Deve rejeitar (4xx) uma tentativa de acesso sem login")
        void deve_Rejeitar_semLogin() throws Exception {
            mockMvc.perform(get("/api/funcionarios"))
                    .andExpect(status().is4xxClientError());
        }
    }
}
