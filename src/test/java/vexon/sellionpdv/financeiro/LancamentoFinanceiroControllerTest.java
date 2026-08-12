package vexon.sellionpdv.financeiro;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import vexon.sellionpdv.common.exception.CodedHttpException;
import vexon.sellionpdv.financeiro.dto.CancelamentoLancamentoRequestDTO;
import vexon.sellionpdv.financeiro.dto.LancamentoCriacaoResultado;
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

    @Test
    void primeiraCriacaoExigeChaveERetornaCreated() throws Exception {
        when(service.criar(eq(request()), eq(java.util.UUID.fromString("0d2dce97-9b70-4b39-9251-790dad6e2755"))))
                .thenReturn(new LancamentoCriacaoResultado(responseAtivo(), false));

        mockMvc.perform(post("/api/financeiro/lancamentos")
                        .header("Idempotency-Key", "0d2dce97-9b70-4b39-9251-790dad6e2755")
                        .contentType(MediaType.APPLICATION_JSON).content(requestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(8));
    }

    @Test
    void replayIdenticoRetornaOkComHeaderExplicito() throws Exception {
        when(service.criar(eq(request()), eq(java.util.UUID.fromString("0d2dce97-9b70-4b39-9251-790dad6e2755"))))
                .thenReturn(new LancamentoCriacaoResultado(responseAtivo(), true));

        mockMvc.perform(post("/api/financeiro/lancamentos")
                        .header("Idempotency-Key", "0d2dce97-9b70-4b39-9251-790dad6e2755")
                        .contentType(MediaType.APPLICATION_JSON).content(requestJson()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Idempotent-Replayed", "true"));
    }

    @Test
    void rejeitaHeaderAusenteOuUuidInvalidoAntesDoService() throws Exception {
        mockMvc.perform(post("/api/financeiro/lancamentos")
                        .contentType(MediaType.APPLICATION_JSON).content(requestJson()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/financeiro/lancamentos")
                        .header("Idempotency-Key", "nao-e-uuid")
                        .contentType(MediaType.APPLICATION_JSON).content(requestJson()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void reusoComOutroPayloadRetornaConflict() throws Exception {
        when(service.criar(eq(request()), eq(java.util.UUID.fromString("0d2dce97-9b70-4b39-9251-790dad6e2755"))))
                .thenThrow(new CodedHttpException(org.springframework.http.HttpStatus.CONFLICT,
                        "IDEMPOTENCY_KEY_REUSED", "Chave reutilizada."));

        mockMvc.perform(post("/api/financeiro/lancamentos")
                        .header("Idempotency-Key", "0d2dce97-9b70-4b39-9251-790dad6e2755")
                        .contentType(MediaType.APPLICATION_JSON).content(requestJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    private LancamentoResponseDTO responseCancelado() {
        return new LancamentoResponseDTO(7L, "Aluguel", new BigDecimal("250.00"), "ALUGUEL",
                LocalDate.of(2026, 8, 1), OffsetDateTime.parse("2026-08-01T10:00:00Z"), "CANCELADO",
                "Duplicidade", OffsetDateTime.parse("2026-08-12T12:00:00Z"), 10L);
    }

    private LancamentoResponseDTO responseAtivo() {
        return new LancamentoResponseDTO(8L, "Aluguel", new BigDecimal("250.00"), "ALUGUEL",
                LocalDate.of(2026, 8, 1), OffsetDateTime.parse("2026-08-01T10:00:00Z"), "ATIVO",
                null, null, null);
    }

    private vexon.sellionpdv.financeiro.dto.LancamentoRequestDTO request() {
        return new vexon.sellionpdv.financeiro.dto.LancamentoRequestDTO("Aluguel", new BigDecimal("250.00"),
                CategoriaLancamento.ALUGUEL, LocalDate.of(2026, 8, 1));
    }

    private String requestJson() {
        return "{\"descricao\":\"Aluguel\",\"valor\":250.00,\"categoria\":\"ALUGUEL\",\"dataReferencia\":\"2026-08-01\"}";
    }
}
