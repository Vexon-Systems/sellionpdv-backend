package vexon.sellionpdv.produto;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import vexon.sellionpdv.common.exception.BusinessException;
import vexon.sellionpdv.common.exception.ResourceNotFoundException;
import vexon.sellionpdv.config.GlobalExceptionHandler;
import vexon.sellionpdv.produto.dto.ProdutoRequestDTO;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static vexon.sellionpdv.produto.ProdutoTestFixtures.*;

/**
 * Testes HTTP do ProdutoController via MockMvc standalone.
 * Cobrem: roteamento, status codes, Bean Validation e mapeamento de exceções
 * via GlobalExceptionHandler.
 *
 * ProdutoController não usa @AuthenticationPrincipal — setup é mais simples que
 * VendaControllerTest (sem AuthenticationPrincipalArgumentResolver / SecurityContext).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProdutoController")
class ProdutoControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private ProdutoService produtoService;
    @InjectMocks private ProdutoController produtoController;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setApplicationContext(new StaticApplicationContext());
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(produtoController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    private String json(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }

    private ProdutoRequestDTO umRequestValido() {
        return umRequestSimples("Açaí 500ml", new BigDecimal("15.50"), 1L);
    }

    // ─── POST /api/produtos ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/produtos")
    class PostProduto {

        @Test
        @DisplayName("Deve retornar 201 e o DTO quando criação é bem-sucedida")
        void deve_Retornar201_quando_CriacaoComSucesso() throws Exception {
            when(produtoService.criarProduto(any()))
                    .thenReturn(umResponseDTO(100L, "Açaí 500ml", new BigDecimal("15.50")));

            mockMvc.perform(post("/api/produtos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(umRequestValido())))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(100))
                    .andExpect(jsonPath("$.nome").value("Açaí 500ml"));
        }

        @Test
        @DisplayName("Deve retornar 400 quando Bean Validation falha (nome em branco)")
        void deve_Retornar400_quando_NomeEmBranco() throws Exception {
            ProdutoRequestDTO invalido = new ProdutoRequestDTO(
                    "", new BigDecimal("15.50"), BigDecimal.ZERO, true, 1L, null, null);

            mockMvc.perform(post("/api/produtos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(invalido)))
                    .andExpect(status().isBadRequest());

            verify(produtoService, never()).criarProduto(any());
        }

        @Test
        @DisplayName("Deve retornar 422 quando Service lança BusinessException (nome duplicado)")
        void deve_Retornar422_quando_ServiceLancaBusinessException_naCriacao() throws Exception {
            when(produtoService.criarProduto(any()))
                    .thenThrow(new BusinessException("Já existe um produto cadastrado com este nome."));

            mockMvc.perform(post("/api/produtos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(umRequestValido())))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("Deve retornar 404 quando Service lança ResourceNotFoundException (categoria)")
        void deve_Retornar404_quando_CategoriaInexistente() throws Exception {
            when(produtoService.criarProduto(any()))
                    .thenThrow(new ResourceNotFoundException("Categoria não encontrada."));

            mockMvc.perform(post("/api/produtos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(umRequestValido())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Deve retornar 400 quando categoriaId é nulo (Bean Validation @NotNull)")
        void deve_Retornar400_quando_CategoriaIdNulo() throws Exception {
            ProdutoRequestDTO invalido = new ProdutoRequestDTO(
                    "Açaí 500ml", new BigDecimal("15.50"), BigDecimal.ZERO, true, null, null, null);

            mockMvc.perform(post("/api/produtos")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(invalido)))
                    .andExpect(status().isBadRequest());

            verify(produtoService, never()).criarProduto(any());
        }
    }

    // ─── GET /api/produtos ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/produtos")
    class GetProdutos {

        @Test
        @DisplayName("Deve retornar 200 com lista de produtos")
        void deve_Retornar200_quando_HaProdutosCadastrados() throws Exception {
            when(produtoService.listarProdutos()).thenReturn(List.of(
                    umResponseDTO(100L, "Açaí 500ml", new BigDecimal("15.50")),
                    umResponseDTO(101L, "Smoothie", new BigDecimal("12.00"))
            ));

            mockMvc.perform(get("/api/produtos"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].id").value(100))
                    .andExpect(jsonPath("$[1].nome").value("Smoothie"));
        }

        @Test
        @DisplayName("Deve retornar 200 com array vazio quando não há produtos")
        void deve_Retornar200ComArrayVazio_quando_NaoHaProdutos() throws Exception {
            when(produtoService.listarProdutos()).thenReturn(List.of());

            mockMvc.perform(get("/api/produtos"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    // ─── GET /api/produtos/{id} ───────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/produtos/{id}")
    class GetProdutoPorId {

        @Test
        @DisplayName("Deve retornar 200 com o produto quando id existe")
        void deve_Retornar200_quando_IdExiste() throws Exception {
            when(produtoService.buscarPorId(100L))
                    .thenReturn(umResponseDTO(100L, "Açaí 500ml", new BigDecimal("15.50")));

            mockMvc.perform(get("/api/produtos/100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(100))
                    .andExpect(jsonPath("$.nome").value("Açaí 500ml"));
        }

        @Test
        @DisplayName("Deve retornar 404 quando Service lança ResourceNotFoundException")
        void deve_Retornar404_quando_BuscaPorIdNaoEncontra() throws Exception {
            when(produtoService.buscarPorId(999L))
                    .thenThrow(new ResourceNotFoundException("Produto não encontrado."));

            mockMvc.perform(get("/api/produtos/999"))
                    .andExpect(status().isNotFound());
        }
    }

    // ─── PUT /api/produtos/{id} ───────────────────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/produtos/{id}")
    class PutProduto {

        @Test
        @DisplayName("Deve retornar 200 com o produto atualizado")
        void deve_Retornar200_quando_AtualizacaoComSucesso() throws Exception {
            when(produtoService.atualizarProduto(eq(100L), any()))
                    .thenReturn(umResponseDTO(100L, "Açaí 500ml", new BigDecimal("20.00")));

            mockMvc.perform(put("/api/produtos/100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(umRequestValido())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.precoBase").value(20.00));
        }

        @Test
        @DisplayName("Deve retornar 422 quando Service lança BusinessException (custo > preço)")
        void deve_Retornar422_quando_ServiceLancaBusinessException_naAtualizacao() throws Exception {
            when(produtoService.atualizarProduto(eq(100L), any()))
                    .thenThrow(new BusinessException("O custo estimado não pode ser maior que o preço de venda."));

            mockMvc.perform(put("/api/produtos/100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(umRequestValido())))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("Deve retornar 400 quando Bean Validation falha (nome em branco)")
        void deve_Retornar400_quando_BeanValidationFalhaNaAtualizacao() throws Exception {
            ProdutoRequestDTO invalido = new ProdutoRequestDTO(
                    "", new BigDecimal("15.50"), BigDecimal.ZERO, true, 1L, null, null);

            mockMvc.perform(put("/api/produtos/100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(invalido)))
                    .andExpect(status().isBadRequest());

            verify(produtoService, never()).atualizarProduto(anyLong(), any());
        }

        @Test
        @DisplayName("Deve retornar 404 quando produto a atualizar não existe")
        void deve_Retornar404_quando_ProdutoInexistenteNaAtualizacao() throws Exception {
            when(produtoService.atualizarProduto(eq(999L), any()))
                    .thenThrow(new ResourceNotFoundException("Produto não encontrado."));

            mockMvc.perform(put("/api/produtos/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(umRequestValido())))
                    .andExpect(status().isNotFound());
        }
    }

    // ─── DELETE /api/produtos/{id} ────────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/produtos/{id}")
    class DeleteProduto {

        @Test
        @DisplayName("Deve retornar 204 quando inativação é bem-sucedida")
        void deve_Retornar204_quando_InativacaoComSucesso() throws Exception {
            doNothing().when(produtoService).inativarProduto(100L);

            mockMvc.perform(delete("/api/produtos/100"))
                    .andExpect(status().isNoContent());

            verify(produtoService).inativarProduto(100L);
        }

        @Test
        @DisplayName("Deve retornar 404 quando produto não existe")
        void deve_Retornar404_quando_InativacaoNaoEncontra() throws Exception {
            doThrow(new ResourceNotFoundException("Produto não encontrado."))
                    .when(produtoService).inativarProduto(999L);

            mockMvc.perform(delete("/api/produtos/999"))
                    .andExpect(status().isNotFound());
        }
    }

    // ─── POST /api/produtos/upload ────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/produtos/upload")
    class PostUpload {

        @Test
        @DisplayName("Deve retornar 200 com {url} quando upload é bem-sucedido")
        void deve_Retornar200_quando_UploadComSucesso() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "foto.png", "image/png", "conteudo".getBytes());

            when(produtoService.uploadImagem(any()))
                    .thenReturn("http://test/uploads/abc-123.png");

            mockMvc.perform(multipart("/api/produtos/upload").file(file))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.url").value("http://test/uploads/abc-123.png"));
        }

        @Test
        @DisplayName("Deve retornar 422 quando Service lança BusinessException (arquivo inválido)")
        void deve_Retornar422_quando_ServiceLancaBusinessException_noUpload() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "doc.pdf", "application/pdf", "conteudo".getBytes());

            when(produtoService.uploadImagem(any()))
                    .thenThrow(new BusinessException("Tipo de arquivo não permitido."));

            mockMvc.perform(multipart("/api/produtos/upload").file(file))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("Deve retornar 400 quando a parte 'file' do multipart está ausente")
        void deve_Retornar400_quando_FileAusente() throws Exception {
            mockMvc.perform(multipart("/api/produtos/upload"))
                    .andExpect(status().isBadRequest());

            verify(produtoService, never()).uploadImagem(any());
        }
    }
}
