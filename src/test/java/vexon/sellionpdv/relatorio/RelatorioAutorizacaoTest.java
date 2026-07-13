package vexon.sellionpdv.relatorio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regressão do SAST-12: @PreAuthorize movido de método para classe em
 * RelatorioController. O motivo não é só estilo — GET /api/relatorios/caixas
 * (listarCaixas) não tinha NENHUMA anotação de autorização antes desta correção,
 * ao contrário de todos os outros métodos do controller. Contexto Spring completo
 * (@AutoConfigureMockMvc), não MockMvc standalone, para exercitar o @PreAuthorize real.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("RelatorioController — autorização (SAST-12)")
class RelatorioAutorizacaoTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "OPERADOR")
    @DisplayName("OPERADOR: GET /api/relatorios/caixas deve receber 403 (não tinha nenhuma checagem antes da correção)")
    void operador_ListarCaixas_deveReceber403() throws Exception {
        mockMvc.perform(get("/api/relatorios/caixas")
                        .param("dataInicial", "2026-01-01")
                        .param("dataFinal", "2026-01-31"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OPERADOR")
    @DisplayName("OPERADOR: GET /api/relatorios/vendas deve receber 403")
    void operador_ListarVendas_deveReceber403() throws Exception {
        mockMvc.perform(get("/api/relatorios/vendas"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "OPERADOR")
    @DisplayName("OPERADOR: GET /api/relatorios/dre deve receber 403")
    void operador_ObterDre_deveReceber403() throws Exception {
        mockMvc.perform(get("/api/relatorios/dre")
                        .param("dataInicial", "2026-01-01")
                        .param("dataFinal", "2026-01-31"))
                .andExpect(status().isForbidden());
    }
}
