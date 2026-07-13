package vexon.sellionpdv.produto;

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
 * Regressão do SAST-03: Produto/Categoria/Maquininha devem liberar GET para qualquer
 * autenticado (o PDV depende disso para montar a grade de venda e o seletor de
 * maquininha no checkout — ver docs/security/relatorio-sast-frontend.md do repositório
 * do frontend) mas travar escrita (POST/PUT/DELETE/upload) para ADMIN. Modificador não
 * tem nenhum consumidor no PDV, então a classe inteira fica ADMIN-only.
 *
 * Mesmo padrão de FuncionarioAutorizacaoTest: contexto Spring completo
 * (@AutoConfigureMockMvc), não MockMvc standalone, para exercitar o @PreAuthorize real.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Catálogo (Produto/Categoria/Maquininha/Modificador) — autorização (SAST-03)")
class CatalogoAutorizacaoTest {

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("ProdutoController")
    class Produto {

        private static final String CORPO_VALIDO = """
                {"nome": "Produto Teste", "precoBase": 10.00, "ativo": true, "categoriaId": 1}
                """;

        @Test
        @WithMockUser(roles = "OPERADOR")
        @DisplayName("OPERADOR: GET /api/produtos deve funcionar (PDV depende disso)")
        void operador_Listar_deveFuncionar() throws Exception {
            mockMvc.perform(get("/api/produtos"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "OPERADOR")
        @DisplayName("OPERADOR: GET /api/produtos/{id} deve funcionar (404 esperado em banco vazio, não 403)")
        void operador_Buscar_deveFuncionar() throws Exception {
            mockMvc.perform(get("/api/produtos/1"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser(roles = "OPERADOR")
        @DisplayName("OPERADOR: POST /api/produtos deve receber 403")
        void operador_Criar_deveReceber403() throws Exception {
            mockMvc.perform(post("/api/produtos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CORPO_VALIDO))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "OPERADOR")
        @DisplayName("OPERADOR: PUT /api/produtos/{id} deve receber 403")
        void operador_Atualizar_deveReceber403() throws Exception {
            mockMvc.perform(put("/api/produtos/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CORPO_VALIDO))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "OPERADOR")
        @DisplayName("OPERADOR: DELETE /api/produtos/{id} deve receber 403")
        void operador_Deletar_deveReceber403() throws Exception {
            mockMvc.perform(delete("/api/produtos/1"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "OPERADOR")
        @DisplayName("OPERADOR: POST /api/produtos/upload deve receber 403")
        void operador_Upload_deveReceber403() throws Exception {
            mockMvc.perform(multipart("/api/produtos/upload")
                            .part(new org.springframework.mock.web.MockPart(
                                    "file", "imagem.png", "conteudo".getBytes())))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("CategoriaController")
    class Categoria {

        private static final String CORPO_VALIDO = """
                {"nome": "Categoria Teste"}
                """;

        @Test
        @WithMockUser(roles = "OPERADOR")
        @DisplayName("OPERADOR: GET /api/categorias deve funcionar (PDV depende disso)")
        void operador_Listar_deveFuncionar() throws Exception {
            mockMvc.perform(get("/api/categorias"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "OPERADOR")
        @DisplayName("OPERADOR: POST /api/categorias deve receber 403")
        void operador_Criar_deveReceber403() throws Exception {
            mockMvc.perform(post("/api/categorias")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CORPO_VALIDO))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "OPERADOR")
        @DisplayName("OPERADOR: PUT /api/categorias/{id} deve receber 403")
        void operador_Atualizar_deveReceber403() throws Exception {
            mockMvc.perform(put("/api/categorias/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CORPO_VALIDO))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "OPERADOR")
        @DisplayName("OPERADOR: DELETE /api/categorias/{id} deve receber 403")
        void operador_Deletar_deveReceber403() throws Exception {
            mockMvc.perform(delete("/api/categorias/1"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("MaquininhaController")
    class Maquininha {

        private static final String CORPO_VALIDO = """
                {"nome": "Maquininha Teste", "marca": "Stone", "taxaDebito": 1.5, "taxaCredito": 3.0, "ativo": true}
                """;

        @Test
        @WithMockUser(roles = "OPERADOR")
        @DisplayName("OPERADOR: GET /api/maquininhas deve funcionar (checkout depende disso)")
        void operador_Listar_deveFuncionar() throws Exception {
            mockMvc.perform(get("/api/maquininhas"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "OPERADOR")
        @DisplayName("OPERADOR: POST /api/maquininhas deve receber 403")
        void operador_Criar_deveReceber403() throws Exception {
            mockMvc.perform(post("/api/maquininhas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CORPO_VALIDO))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "OPERADOR")
        @DisplayName("OPERADOR: PUT /api/maquininhas/{id} deve receber 403")
        void operador_Atualizar_deveReceber403() throws Exception {
            mockMvc.perform(put("/api/maquininhas/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CORPO_VALIDO))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "OPERADOR")
        @DisplayName("OPERADOR: DELETE /api/maquininhas/{id} deve receber 403")
        void operador_Deletar_deveReceber403() throws Exception {
            mockMvc.perform(delete("/api/maquininhas/1"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("ModificadorController — classe inteira ADMIN-only")
    class Modificador {

        private static final String CORPO_VALIDO = """
                {"nome": "Grupo Teste", "opcoes": [{"nome": "Opcao Teste", "precoAdicional": 0}]}
                """;

        @Test
        @WithMockUser(roles = "OPERADOR")
        @DisplayName("OPERADOR: GET /api/modificadores deve receber 403 (nenhuma tela do PDV usa)")
        void operador_Listar_deveReceber403() throws Exception {
            mockMvc.perform(get("/api/modificadores"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "OPERADOR")
        @DisplayName("OPERADOR: POST /api/modificadores deve receber 403")
        void operador_Criar_deveReceber403() throws Exception {
            mockMvc.perform(post("/api/modificadores")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CORPO_VALIDO))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "OPERADOR")
        @DisplayName("OPERADOR: PUT /api/modificadores/{id} deve receber 403")
        void operador_Atualizar_deveReceber403() throws Exception {
            mockMvc.perform(put("/api/modificadores/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CORPO_VALIDO))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "OPERADOR")
        @DisplayName("OPERADOR: DELETE /api/modificadores/{id} deve receber 403")
        void operador_Deletar_deveReceber403() throws Exception {
            mockMvc.perform(delete("/api/modificadores/1"))
                    .andExpect(status().isForbidden());
        }
    }
}
