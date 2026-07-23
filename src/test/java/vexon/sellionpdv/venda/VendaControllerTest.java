package vexon.sellionpdv.venda;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import vexon.sellionpdv.common.exception.BusinessException;
import vexon.sellionpdv.common.exception.CodedHttpException;
import vexon.sellionpdv.common.exception.ResourceNotFoundException;
import vexon.sellionpdv.config.GlobalExceptionHandler;
import vexon.sellionpdv.relatorio.pdf.ReciboVendaPdfService;
import vexon.sellionpdv.venda.dto.ItemVendaRequestDTO;
import vexon.sellionpdv.venda.dto.CancelamentoVendaRequestDTO;
import vexon.sellionpdv.venda.dto.VendaRequestDTO;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static vexon.sellionpdv.venda.VendaTestFixtures.*;

/**
 * Testes de integração HTTP do VendaController via MockMvc standalone.
 * Cobrem: roteamento, status codes HTTP, Bean Validation e mapeamento de exceções.
 *
 * Inconsistências documentadas vs. especificação original:
 * - Cenário 7: pedido original era 409 Conflict. GlobalExceptionHandler mapeia
 *   BusinessException para 422 UNPROCESSABLE_ENTITY — implementado com o status correto.
 * - VendaResponseDTO não tem campo "token"; o campo correto é "idempotencyKey".
 * - SEL-SEC-003 passou a validar desconto e motivo no backend antes da persistência.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VendaController")
class VendaControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private VendaService vendaService;

    @Mock
    private ReciboVendaPdfService reciboVendaPdfService;

    @InjectMocks
    private VendaController vendaController;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setApplicationContext(new StaticApplicationContext());
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(vendaController)
                .setControllerAdvice(new GlobalExceptionHandler())
                // AuthenticationPrincipalArgumentResolver é necessário: VendaController.registrarVenda()
                // usa @AuthenticationPrincipal UserDetails e chama userDetails.getUsername() na assinatura.
                // Sem autenticação no contexto, userDetails seria null → NullPointerException → 500.
                // Testes de 401 (sem autenticação) pertencem à camada de filtros de segurança e
                // não são viáveis neste setup standalone (SecurityFilter não é aplicado aqui).
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setValidator(validator)
                .build();

        var userDetails = User.withUsername("operador@test.com")
                .password("").roles("ADMIN").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private String json(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    // ─── POST /api/vendas — sucesso ──────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/vendas — sucesso")
    class PostVendaSucesso {

        @Test
        @DisplayName("Deve retornar 201 quando a venda é registrada com sucesso")
        void deve_Retornar201_quando_VendaRegistradaComSucesso() throws Exception {
            UUID key = UUID.randomUUID();
            when(vendaService.registrarVenda(any(), any(), any())).thenReturn(umaVendaResponseDTO(key));

            mockMvc.perform(post("/api/vendas")
                            .header("Idempotency-Key", key.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(umaVendaRequestDTOValida())))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value("CONCLUIDA"))
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.formaPagamento").value("DINHEIRO"))
                    .andExpect(jsonPath("$.motivoDesconto").doesNotExist());
        }
    }

    // ─── POST /api/vendas — erros de negócio ────────────────────────────────────

    @Nested
    @DisplayName("POST /api/vendas — erros de negócio")
    class PostVendaErros {

        /**
         * Status correto: 422 UNPROCESSABLE_ENTITY.
         * GlobalExceptionHandler.handleBusiness() retorna 422, não 409.
         */
        @Test
        @DisplayName("Deve retornar 422 quando a chave de idempotência é duplicada")
        void deve_Retornar422_quando_ChaveIdempotenciaDuplicada() throws Exception {
            UUID key = UUID.randomUUID();
            when(vendaService.registrarVenda(any(), any(), any()))
                    .thenThrow(new BusinessException("Venda já processada com esta chave."));

            mockMvc.perform(post("/api/vendas")
                            .header("Idempotency-Key", key.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(umaVendaRequestDTOValida())))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("Deve retornar 404 quando o produto não é encontrado")
        void deve_Retornar404_quando_ProdutoNaoEncontrado() throws Exception {
            UUID key = UUID.randomUUID();
            when(vendaService.registrarVenda(any(), any(), any()))
                    .thenThrow(new ResourceNotFoundException("Produto não encontrado: 999"));

            mockMvc.perform(post("/api/vendas")
                            .header("Idempotency-Key", key.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(umaVendaRequestDTOValida())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Deve retornar 404 quando não há caixa aberto")
        void deve_Retornar404_quando_NenhumCaixaAberto() throws Exception {
            UUID key = UUID.randomUUID();
            when(vendaService.registrarVenda(any(), any(), any()))
                    .thenThrow(new ResourceNotFoundException("Nenhum caixa aberto encontrado para o tenant atual."));

            mockMvc.perform(post("/api/vendas")
                            .header("Idempotency-Key", key.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(umaVendaRequestDTOValida())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("SEL-SEC-003 — deve retornar 422 e código estável acima da alçada")
        void deve_Retornar422ComCodigo_quando_DescontoAcimaDaAlcada() throws Exception {
            UUID key = UUID.randomUUID();
            when(vendaService.registrarVenda(any(), any(), any())).thenThrow(new CodedHttpException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "DESCONTO_ACIMA_DA_ALCADA",
                    "O desconto informado excede a alçada do usuário."));

            mockMvc.perform(post("/api/vendas")
                            .header("Idempotency-Key", key.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(umaVendaRequestDTOValida())))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("DESCONTO_ACIMA_DA_ALCADA"));
        }

        @Test
        @DisplayName("SEL-SEC-003 — deve retornar 403 e código estável sem alçada")
        void deve_Retornar403ComCodigo_quando_DescontoNaoAutorizado() throws Exception {
            UUID key = UUID.randomUUID();
            when(vendaService.registrarVenda(any(), any(), any())).thenThrow(new CodedHttpException(
                    HttpStatus.FORBIDDEN,
                    "DESCONTO_NAO_AUTORIZADO",
                    "O usuário não possui permissão para conceder desconto."));

            mockMvc.perform(post("/api/vendas")
                            .header("Idempotency-Key", key.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(umaVendaRequestDTOValida())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("DESCONTO_NAO_AUTORIZADO"));
        }

        @Test
        @DisplayName("SEL-SEC-004 — deve retornar 422 e código estável para matriz incoerente")
        void deve_Retornar422ComCodigo_quando_MatrizPagamentoInvalida() throws Exception {
            UUID key = UUID.randomUUID();
            when(vendaService.registrarVenda(any(), any(), any())).thenThrow(new CodedHttpException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "MATRIZ_PAGAMENTO_INVALIDA",
                    "A combinação entre forma de pagamento, maquininha e bandeira é inválida."));

            mockMvc.perform(post("/api/vendas")
                            .header("Idempotency-Key", key.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(umaVendaRequestDTOValida())))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("MATRIZ_PAGAMENTO_INVALIDA"));
        }
    }

    // ─── POST /api/vendas — validação de entrada ─────────────────────────────────

    @Nested
    @DisplayName("POST /api/vendas — Bean Validation (400)")
    class PostVendaValidacao {

        /**
         * Cenário 2 reinterpretado: @NotEmpty no VendaRequestDTO.itens é
         * aplicado pelo @Valid no Controller — não existe no VendaService.
         */
        @Test
        @DisplayName("Deve retornar 400 quando a lista de itens está vazia")
        void deve_Retornar400_quando_ListaDeItensVazia() throws Exception {
            VendaRequestDTO dtoInvalido = new VendaRequestDTO(
                    List.of(), // viola @NotEmpty
                    FormaPagamento.DINHEIRO, null, null, null
            );

            mockMvc.perform(post("/api/vendas")
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(dtoInvalido)))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Cenário 3a reinterpretado: @Positive no ItemVendaRequestDTO.quantidade
         * é aplicado pelo Controller — quantity=0 é inválido.
         */
        @Test
        @DisplayName("Deve retornar 400 quando a quantidade do item é zero")
        void deve_Retornar400_quando_QuantidadeDoItemForZero() throws Exception {
            VendaRequestDTO dtoInvalido = new VendaRequestDTO(
                    List.of(new ItemVendaRequestDTO(1L, 0, List.of())), // viola @Positive
                    FormaPagamento.DINHEIRO, null, null, null
            );

            mockMvc.perform(post("/api/vendas")
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(dtoInvalido)))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Cenário 3b: quantidade negativa também viola @Positive.
         */
        @Test
        @DisplayName("Deve retornar 400 quando a quantidade do item é negativa")
        void deve_Retornar400_quando_QuantidadeDoItemForNegativa() throws Exception {
            VendaRequestDTO dtoInvalido = new VendaRequestDTO(
                    List.of(new ItemVendaRequestDTO(1L, -1, List.of())), // viola @Positive
                    FormaPagamento.DINHEIRO, null, null, null
            );

            mockMvc.perform(post("/api/vendas")
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(dtoInvalido)))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Cenário 4 reinterpretado: proteção contra chave nula existe no protocolo HTTP.
         * Header obrigatório ausente → Spring MVC retorna 400 antes de chegar ao service.
         */
        @Test
        @DisplayName("Deve retornar 400 quando o header Idempotency-Key está ausente")
        void deve_Retornar400_quando_HeaderIdempotencyKeyAusente() throws Exception {
            mockMvc.perform(post("/api/vendas")
                            // sem o header Idempotency-Key
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(umaVendaRequestDTOValida())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Deve retornar 400 quando a forma de pagamento está ausente")
        void deve_Retornar400_quando_FormaPagamentoAusente() throws Exception {
            VendaRequestDTO dtoInvalido = new VendaRequestDTO(
                    List.of(new ItemVendaRequestDTO(1L, 1, List.of())),
                    null, // viola @NotNull
                    null, null, null
            );

            mockMvc.perform(post("/api/vendas")
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(dtoInvalido)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Deve retornar 400 quando o id do produto do item é nulo")
        void deve_Retornar400_quando_ProdutoIdDoItemNulo() throws Exception {
            VendaRequestDTO dtoInvalido = new VendaRequestDTO(
                    List.of(new ItemVendaRequestDTO(null, 1, List.of())), // viola @NotNull em produtoId
                    FormaPagamento.DINHEIRO, null, null, null
            );

            mockMvc.perform(post("/api/vendas")
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(dtoInvalido)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Deve retornar 400 quando o corpo da requisição é inválido")
        void deve_Retornar400_quando_CorpoDaRequisicaoInvalido() throws Exception {
            mockMvc.perform(post("/api/vendas")
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ json inválido }"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("SEL-SEC-003 — deve rejeitar desconto com mais de duas casas")
        void deve_Retornar400ComCodigo_quando_DescontoTemEscalaInvalida() throws Exception {
            VendaRequestDTO dtoInvalido = new VendaRequestDTO(
                    List.of(new ItemVendaRequestDTO(1L, 1, List.of())),
                    FormaPagamento.DINHEIRO, null, null, new BigDecimal("10.000"), "Motivo"
            );

            mockMvc.perform(post("/api/vendas")
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(dtoInvalido)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"));
        }

        @Test
        @DisplayName("SEL-SEC-003 — deve rejeitar motivo acima de 500 caracteres")
        void deve_Retornar400ComCodigo_quando_MotivoExcedeLimite() throws Exception {
            VendaRequestDTO dtoInvalido = new VendaRequestDTO(
                    List.of(new ItemVendaRequestDTO(1L, 1, List.of())),
                    FormaPagamento.DINHEIRO, null, null, new BigDecimal("1.00"), "x".repeat(501)
            );

            mockMvc.perform(post("/api/vendas")
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(dtoInvalido)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"));
        }
    }

    // ─── POST /api/vendas/{id}/cancelar ─────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/vendas/{id}/cancelar")
    class CancelarVenda {

        @Test
        @DisplayName("Deve retornar 200 e encaminhar o usuário autenticado")
        void deve_Retornar200_quando_CancelamentoAceito() throws Exception {
            mockMvc.perform(post("/api/vendas/1/cancelar")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"justificativa\":\"Cliente desistiu\"}"))
                    .andExpect(status().isOk());

            verify(vendaService).cancelarVenda(eq(1L), any(CancelamentoVendaRequestDTO.class),
                    eq("operador@test.com"));
        }

        @Test
        @DisplayName("Deve retornar 400 para justificativa vazia")
        void deve_Retornar400_quando_JustificativaVazia() throws Exception {
            mockMvc.perform(post("/api/vendas/1/cancelar")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"justificativa\":\"   \"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Deve retornar 400 para justificativa nula")
        void deve_Retornar400_quando_JustificativaNula() throws Exception {
            mockMvc.perform(post("/api/vendas/1/cancelar")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"justificativa\":null}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Deve retornar 400 para justificativa vazia sem espaços")
        void deve_Retornar400_quando_JustificativaEhStringVazia() throws Exception {
            mockMvc.perform(post("/api/vendas/1/cancelar")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"justificativa\":\"\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Deve retornar 400 para justificativa acima de 500 caracteres")
        void deve_Retornar400_quando_JustificativaExcedeLimite() throws Exception {
            String justificativa = "x".repeat(501);
            mockMvc.perform(post("/api/vendas/1/cancelar")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new CancelamentoVendaRequestDTO(justificativa))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Deve ignorar campos de identidade e horário injetados no body")
        void deve_UsarIdentidadeAutenticada_quando_BodyTentaAdulterarContexto() throws Exception {
            mockMvc.perform(post("/api/vendas/1/cancelar")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "justificativa": "Tentativa controlada",
                                      "tenantId": 999,
                                      "usuarioId": 999,
                                      "papel": "ROLE_ADMIN",
                                      "dataVenda": "2026-07-22T16:00:00Z"
                                    }
                                    """))
                    .andExpect(status().isOk());

            verify(vendaService).cancelarVenda(eq(1L), any(CancelamentoVendaRequestDTO.class),
                    eq("operador@test.com"));
        }

        @Test
        @DisplayName("Deve retornar 403 quando a autoria ou papel não autoriza")
        void deve_Retornar403_quando_AcessoNegado() throws Exception {
            doThrow(new AccessDeniedException("Sem permissão"))
                    .when(vendaService).cancelarVenda(eq(1L), any(), eq("operador@test.com"));

            mockMvc.perform(post("/api/vendas/1/cancelar")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"justificativa\":\"Tentativa\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Deve retornar 404 sem diferenciar venda ausente ou cross-tenant")
        void deve_Retornar404_quando_VendaNaoEncontrada() throws Exception {
            doThrow(new ResourceNotFoundException("Venda não encontrada."))
                    .when(vendaService).cancelarVenda(eq(99L), any(), eq("operador@test.com"));

            mockMvc.perform(post("/api/vendas/99/cancelar")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"justificativa\":\"Tentativa\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Venda não encontrada."));
        }

        @Test
        @DisplayName("Deve retornar 422 quando a janela ou estado impede cancelamento")
        void deve_Retornar422_quando_RegraDeNegocioRejeita() throws Exception {
            doThrow(new BusinessException("O prazo de 10 minutos para cancelamento da venda foi excedido."))
                    .when(vendaService).cancelarVenda(eq(1L), any(), eq("operador@test.com"));

            mockMvc.perform(post("/api/vendas/1/cancelar")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"justificativa\":\"Tarde\"}"))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    // ─── GET /api/vendas/{id}/recibo.pdf ────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/vendas/{id}/recibo.pdf")
    class BaixarRecibo {

        @Test
        @DisplayName("Deve retornar 200 com application/pdf e Content-Disposition de anexo")
        void deve_Retornar200_comPdf_eHeadersCorretos() throws Exception {
            byte[] pdfFake = "%PDF-1.4 fake".getBytes();
            when(reciboVendaPdfService.gerarRecibo(eq(1L))).thenReturn(pdfFake);

            mockMvc.perform(get("/api/vendas/1/recibo.pdf"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                    .andExpect(header().string("Content-Disposition",
                            "attachment; filename=\"recibo-venda-1.pdf\""))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().bytes(pdfFake));
        }

        @Test
        @DisplayName("Deve retornar 404 quando a venda não existe")
        void deve_Retornar404_quando_VendaNaoExiste() throws Exception {
            when(reciboVendaPdfService.gerarRecibo(eq(99L)))
                    .thenThrow(new ResourceNotFoundException(
                            "Venda não encontrada ou não pertence à franquia."));

            mockMvc.perform(get("/api/vendas/99/recibo.pdf"))
                    .andExpect(status().isNotFound());
        }
    }
}
