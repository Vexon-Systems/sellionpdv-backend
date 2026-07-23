package vexon.sellionpdv.venda;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("SEL-SEC-002 — defesa de papel no endpoint de cancelamento")
class VendaCancelamentoAutorizacaoTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "AUDITOR")
    @DisplayName("papel desconhecido recebe 403 antes de chegar ao service")
    void papelDesconhecidoRecebe403() throws Exception {
        mockMvc.perform(post("/api/vendas/1/cancelar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justificativa\":\"Tentativa\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OPERADOR")
    @DisplayName("OPERADOR atravessa a defesa de papel e chega à regra contextual")
    void operadorEhPapelPermitido() throws Exception {
        mockMvc.perform(post("/api/vendas/1/cancelar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justificativa\":\"Tentativa\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN atravessa a defesa de papel e chega à regra contextual")
    void adminEhPapelPermitido() throws Exception {
        mockMvc.perform(post("/api/vendas/1/cancelar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justificativa\":\"Tentativa\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("requisição sem autenticação é rejeitada")
    void semAutenticacaoEhRejeitada() throws Exception {
        mockMvc.perform(post("/api/vendas/1/cancelar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justificativa\":\"Tentativa\"}"))
                .andExpect(status().is4xxClientError());
    }
}
