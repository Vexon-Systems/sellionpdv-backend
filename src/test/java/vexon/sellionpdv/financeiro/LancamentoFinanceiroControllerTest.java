package vexon.sellionpdv.financeiro;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import vexon.sellionpdv.config.GlobalExceptionHandler;
import vexon.sellionpdv.financeiro.dto.CancelamentoLancamentoRequestDTO;
import vexon.sellionpdv.financeiro.dto.LancamentoResponseDTO;

@ExtendWith(MockitoExtension.class)
class LancamentoFinanceiroControllerTest {

    @Mock private LancamentoFinanceiroService service;
    @InjectMocks private LancamentoFinanceiroController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setApplicationContext(new StaticApplicationContext());
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).setValidator(validator).build();
    }

    @Test
    void cancelaPorRotaExplicitaComMotivo() throws Exception {
        when(service.cancelar(eq(7L), org.mockito.ArgumentMatchers.any())).thenReturn(responseCancelado());

        mockMvc.perform(post("/api/financeiro/lancamentos/7/cancelamento")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Duplicidade\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"))
                .andExpect(jsonPath("$.motivoCancelamento").value("Duplicidade"));

        ArgumentCaptor<CancelamentoLancamentoRequestDTO> captor = ArgumentCaptor.forClass(CancelamentoLancamentoRequestDTO.class);
        verify(service).cancelar(eq(7L), captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("Duplicidade", captor.getValue().motivo());
    }

    @Test
    void rejeitaMotivoCurto() throws Exception {
        mockMvc.perform(post("/api/financeiro/lancamentos/7/cancelamento")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rotaDeleteAntigaNaoExisteMais() throws Exception {
        mockMvc.perform(delete("/api/financeiro/lancamentos/7"))
                .andExpect(status().isMethodNotAllowed());
    }

    private LancamentoResponseDTO responseCancelado() {
        return new LancamentoResponseDTO(7L, "Aluguel", new BigDecimal("250.00"), "ALUGUEL",
                LocalDate.of(2026, 8, 1), OffsetDateTime.parse("2026-08-01T10:00:00Z"), "CANCELADO",
                "Duplicidade", OffsetDateTime.parse("2026-08-12T12:00:00Z"), 10L);
    }
}
