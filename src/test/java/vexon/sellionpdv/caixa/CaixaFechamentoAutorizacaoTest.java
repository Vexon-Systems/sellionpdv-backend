package vexon.sellionpdv.caixa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("SEL-SEC-008 — autorização dos contratos completos de caixa")
class CaixaFechamentoAutorizacaoTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "operador@test.com", roles = "OPERADOR")
    @DisplayName("operador recebe 403 nas três consultas monetárias completas")
    void deve_BloquearConsultasCompletasParaOperador() throws Exception {
        mockMvc.perform(get("/api/caixa/atual"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/caixa/movimentacao"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/vendas"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "operador@test.com", roles = "OPERADOR")
    @DisplayName("operador possui autorização para a visão operacional")
    void deve_AutorizarVisaoOperacionalParaOperador() throws Exception {
        mockMvc.perform(get("/api/caixa/operacional"))
                .andExpect(result -> assertNotEquals(403, result.getResponse().getStatus()));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    @DisplayName("administrador possui autorização para os contratos completos")
    void deve_AutorizarConsultasCompletasParaAdmin() throws Exception {
        mockMvc.perform(get("/api/caixa/atual"))
                .andExpect(result -> assertNotEquals(403, result.getResponse().getStatus()));
        mockMvc.perform(get("/api/caixa/movimentacao"))
                .andExpect(result -> assertNotEquals(403, result.getResponse().getStatus()));
        mockMvc.perform(get("/api/vendas"))
                .andExpect(result -> assertNotEquals(403, result.getResponse().getStatus()));
    }
}
