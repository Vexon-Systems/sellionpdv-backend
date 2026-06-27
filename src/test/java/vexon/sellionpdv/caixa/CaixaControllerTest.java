package vexon.sellionpdv.caixa;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import vexon.sellionpdv.caixa.dto.MovimentacaoCaixaRequestDTO;
import vexon.sellionpdv.common.exception.BusinessException;
import vexon.sellionpdv.common.exception.ResourceNotFoundException;
import vexon.sellionpdv.config.GlobalExceptionHandler;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vexon.sellionpdv.caixa.CaixaTestFixtures.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CaixaController")
class CaixaControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private CaixaService service;

    @InjectMocks
    private CaixaController controller;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setApplicationContext(new StaticApplicationContext());
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    private String json(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    // ─── GET /api/caixa/atual ────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/caixa/atual")
    class GetCaixaAtual {

        @Test
        @DisplayName("CC1 — deve_Retornar200_com_CaixaResponseDTO_quando_CaixaAberto")
        void deve_Retornar200_com_CaixaResponseDTO_quando_CaixaAberto() throws Exception {
            when(service.buscarCaixaAtual()).thenReturn(umCaixaAberto(umTenant()));

            mockMvc.perform(get("/api/caixa/atual"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.status").value("ABERTO"))
                    .andExpect(jsonPath("$.saldoInicial").value(100.0));
        }

        @Test
        @DisplayName("CC2 — deve_Retornar404_quando_NaoHaCaixaAberto")
        void deve_Retornar404_quando_NaoHaCaixaAberto() throws Exception {
            when(service.buscarCaixaAtual())
                    .thenThrow(new ResourceNotFoundException("Nenhum caixa aberto encontrado para o tenant atual."));

            mockMvc.perform(get("/api/caixa/atual"))
                    .andExpect(status().isNotFound());
        }
    }

    // ─── GET /api/caixa/movimentacao ─────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/caixa/movimentacao")
    class GetMovimentacoes {

        @Test
        @DisplayName("CC3 — deve_Retornar200_com_ListaDeMovimentacoes_quando_Existem")
        void deve_Retornar200_com_ListaDeMovimentacoes_quando_Existem() throws Exception {
            when(service.listarMovimentacoesCaixaAtual())
                    .thenReturn(List.of(umaMovimentacaoResponseDTO()));

            mockMvc.perform(get("/api/caixa/movimentacao"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].tipo").value("SANGRIA"))
                    .andExpect(jsonPath("$[0].valor").value(50.0));
        }

        @Test
        @DisplayName("CC4 — deve_Retornar200_com_ArrayVazio_quando_SemMovimentacoes")
        void deve_Retornar200_com_ArrayVazio_quando_SemMovimentacoes() throws Exception {
            when(service.listarMovimentacoesCaixaAtual()).thenReturn(List.of());

            mockMvc.perform(get("/api/caixa/movimentacao"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    // ─── POST /api/caixa/abrir ───────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/caixa/abrir")
    class PostAbrirCaixa {

        @Test
        @DisplayName("CC5 — deve_Retornar201_com_CaixaResponseDTO_quando_AberturaSucesso")
        void deve_Retornar201_com_CaixaResponseDTO_quando_AberturaSucesso() throws Exception {
            when(service.abrirCaixa(any())).thenReturn(umCaixaAberto(umTenant()));

            mockMvc.perform(post("/api/caixa/abrir")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(umCaixaRequestDTO(new BigDecimal("100.00")))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("ABERTO"))
                    .andExpect(jsonPath("$.id").value(10));
        }

        @Test
        @DisplayName("CC6 — deve_Retornar422_quando_JaExisteCaixaAberto")
        void deve_Retornar422_quando_JaExisteCaixaAberto() throws Exception {
            when(service.abrirCaixa(any()))
                    .thenThrow(new BusinessException("Já existe um caixa aberto."));

            mockMvc.perform(post("/api/caixa/abrir")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(umCaixaRequestDTO(new BigDecimal("100.00")))))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("CC7 — deve_Retornar400_quando_SaldoInicialNulo (@NotNull)")
        void deve_Retornar400_quando_SaldoInicialNulo() throws Exception {
            mockMvc.perform(post("/api/caixa/abrir")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(umCaixaRequestDTO(null))))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─── POST /api/caixa/movimentacao ────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/caixa/movimentacao")
    class PostMovimentacao {

        @Test
        @DisplayName("CC8 — deve_Retornar201_SemBody_quando_MovimentacaoRegistrada")
        void deve_Retornar201_SemBody_quando_MovimentacaoRegistrada() throws Exception {
            mockMvc.perform(post("/api/caixa/movimentacao")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(umaSangriaRequestDTO())))
                    .andExpect(status().isCreated());

            verify(service).registrarMovimentacao(any());
        }

        @Test
        @DisplayName("CC9 — deve_Retornar400_quando_TipoMovimentacaoNulo (@NotNull)")
        void deve_Retornar400_quando_TipoMovimentacaoNulo() throws Exception {
            mockMvc.perform(post("/api/caixa/movimentacao")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new MovimentacaoCaixaRequestDTO(
                                    null, new BigDecimal("50.00"), "teste"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("CC10 — deve_Retornar400_quando_ValorNegativo (@Positive)")
        void deve_Retornar400_quando_ValorNegativo() throws Exception {
            mockMvc.perform(post("/api/caixa/movimentacao")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new MovimentacaoCaixaRequestDTO(
                                    TipoMovimentacaoCaixa.SANGRIA, new BigDecimal("-1"), "teste"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("CC11 — deve_Retornar400_quando_MotivoEmBranco (@NotBlank)")
        void deve_Retornar400_quando_MotivoEmBranco() throws Exception {
            mockMvc.perform(post("/api/caixa/movimentacao")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new MovimentacaoCaixaRequestDTO(
                                    TipoMovimentacaoCaixa.SANGRIA, new BigDecimal("50.00"), ""))))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─── POST /api/caixa/fechar ──────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/caixa/fechar")
    class PostFecharCaixa {

        @Test
        @DisplayName("CC12 — deve_Retornar200_com_CaixaFechamentoResponseDTO_quando_FechamentoOk")
        void deve_Retornar200_com_CaixaFechamentoResponseDTO_quando_FechamentoOk() throws Exception {
            when(service.fecharCaixa(any())).thenReturn(umaCaixaFechamentoResponseDTO());

            mockMvc.perform(post("/api/caixa/fechar")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(umCaixaFechamentoRequestDTO(new BigDecimal("300.00")))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.saldoInicial").value(100.0))
                    .andExpect(jsonPath("$.totalVendasDinheiro").value(200.0))
                    .andExpect(jsonPath("$.furoCaixa").value(0));
        }

        @Test
        @DisplayName("CC13 — deve_Retornar404_quando_NaoHaCaixaAberto_aoFechar")
        void deve_Retornar404_quando_NaoHaCaixaAberto_aoFechar() throws Exception {
            when(service.fecharCaixa(any()))
                    .thenThrow(new ResourceNotFoundException("Nenhum caixa aberto encontrado para o tenant atual."));

            mockMvc.perform(post("/api/caixa/fechar")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(umCaixaFechamentoRequestDTO(new BigDecimal("100.00")))))
                    .andExpect(status().isNotFound());
        }
    }
}
