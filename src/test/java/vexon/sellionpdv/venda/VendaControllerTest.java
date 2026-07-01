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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import vexon.sellionpdv.common.exception.BusinessException;
import vexon.sellionpdv.common.exception.ResourceNotFoundException;
import vexon.sellionpdv.config.GlobalExceptionHandler;
import vexon.sellionpdv.relatorio.pdf.ReciboVendaPdfService;
import vexon.sellionpdv.venda.dto.ItemVendaRequestDTO;
import vexon.sellionpdv.venda.dto.VendaRequestDTO;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
 * - Cenários 1 (desconto > subtotal): VendaService não valida — salva com totalFinal
 *   negativo. Adicionar validação no service antes de escrever esse teste.
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
                    .andExpect(jsonPath("$.formaPagamento").value("DINHEIRO"));
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
