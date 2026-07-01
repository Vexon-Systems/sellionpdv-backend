package vexon.sellionpdv.produto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import vexon.sellionpdv.categoria.Categoria;
import vexon.sellionpdv.categoria.CategoriaRepository;
import vexon.sellionpdv.common.exception.BusinessException;
import vexon.sellionpdv.common.exception.ResourceNotFoundException;
import vexon.sellionpdv.modificador.GrupoModificador;
import vexon.sellionpdv.modificador.GrupoModificadorRepository;
import vexon.sellionpdv.modificador.OpcaoModificador;
import vexon.sellionpdv.produto.dto.ProdutoGrupoRequestDTO;
import vexon.sellionpdv.produto.dto.ProdutoRequestDTO;
import vexon.sellionpdv.produto.dto.ProdutoResponseDTO;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static vexon.sellionpdv.produto.ProdutoTestFixtures.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProdutoService")
class ProdutoServiceTest {

    @Mock private ProdutoRepository produtoRepository;
    @Mock private CategoriaRepository categoriaRepository;
    @Mock private GrupoModificadorRepository grupoRepository;
    @InjectMocks private ProdutoService produtoService;

    /**
     * Compara BigDecimal por valor, ignorando escala.
     * Necessário para margemBruta (scale=2 vs. literais sem escala).
     */
    private static void assertBD(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "BigDecimal esperado=" + expected + " mas foi=" + actual);
    }

    // ─── criarProduto ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("criarProduto")
    class CriarProduto {

        @Test
        @DisplayName("Deve criar produto e persistir entidade com campos corretos")
        void deve_CriarProduto_quando_DadosValidos() {
            Categoria categoria = umaCategoria(1L, "Açaí");
            ProdutoRequestDTO request = umRequestSimples("Açaí 500ml", new BigDecimal("15.50"), 1L);
            Produto salvo = umProduto(100L, "Açaí 500ml",
                    new BigDecimal("15.50"), BigDecimal.ZERO, categoria);

            when(produtoRepository.existsByNomeIgnoreCaseAndAtivoTrue("Açaí 500ml")).thenReturn(false);
            when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria));
            when(produtoRepository.save(any(Produto.class))).thenReturn(salvo);

            produtoService.criarProduto(request);

            ArgumentCaptor<Produto> captor = ArgumentCaptor.forClass(Produto.class);
            verify(produtoRepository).save(captor.capture());
            Produto persistido = captor.getValue();
            assertEquals("Açaí 500ml", persistido.getNome());
            assertBD("15.50", persistido.getPrecoBase());
            assertBD("0", persistido.getCustoEstimado());
            assertEquals(categoria, persistido.getCategoria());
            assertTrue(persistido.getAtivo());
        }

        @Test
        @DisplayName("Deve lançar BusinessException quando já existe produto ativo com o mesmo nome")
        void deve_LancarBusinessException_quando_NomeJaExisteEmProdutoAtivo() {
            ProdutoRequestDTO request = umRequestSimples("Açaí 500ml", new BigDecimal("15.50"), 1L);
            when(produtoRepository.existsByNomeIgnoreCaseAndAtivoTrue("Açaí 500ml")).thenReturn(true);

            assertThrows(BusinessException.class, () -> produtoService.criarProduto(request));

            verify(produtoRepository, never()).save(any(Produto.class));
            verify(categoriaRepository, never()).findById(anyLong());
        }

        @Test
        @DisplayName("Deve lançar ResourceNotFoundException quando categoria informada não existe")
        void deve_LancarResourceNotFoundException_quando_CategoriaInexistente() {
            ProdutoRequestDTO request = umRequestSimples("Açaí 500ml", new BigDecimal("15.50"), 999L);

            when(produtoRepository.existsByNomeIgnoreCaseAndAtivoTrue("Açaí 500ml")).thenReturn(false);
            when(categoriaRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> produtoService.criarProduto(request));

            verify(produtoRepository, never()).save(any(Produto.class));
        }

        @Test
        @DisplayName("Deve lançar BusinessException quando custo estimado é maior que o preço base")
        void deve_LancarBusinessException_quando_CustoMaiorQuePreco() {
            ProdutoRequestDTO request = umRequest("Açaí 500ml",
                    new BigDecimal("10.00"), new BigDecimal("12.00"), 1L, null);

            when(produtoRepository.existsByNomeIgnoreCaseAndAtivoTrue("Açaí 500ml")).thenReturn(false);

            assertThrows(BusinessException.class, () -> produtoService.criarProduto(request));

            verify(categoriaRepository, never()).findById(anyLong());
            verify(produtoRepository, never()).save(any(Produto.class));
        }

        @Test
        @DisplayName("Deve aceitar custo igual ao preço base (boundary da validação)")
        void deve_CriarProduto_quando_CustoIgualAoPreco() {
            Categoria categoria = umaCategoria(1L, "Açaí");
            ProdutoRequestDTO request = umRequest("Açaí 500ml",
                    new BigDecimal("10.00"), new BigDecimal("10.00"), 1L, null);
            Produto salvo = umProduto(100L, "Açaí 500ml",
                    new BigDecimal("10.00"), new BigDecimal("10.00"), categoria);

            when(produtoRepository.existsByNomeIgnoreCaseAndAtivoTrue("Açaí 500ml")).thenReturn(false);
            when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria));
            when(produtoRepository.save(any(Produto.class))).thenReturn(salvo);

            assertDoesNotThrow(() -> produtoService.criarProduto(request));
            verify(produtoRepository).save(any(Produto.class));
        }

        @Test
        @DisplayName("Deve aceitar custo nulo sem disparar validação de margem")
        void deve_CriarProduto_quando_CustoNulo() {
            Categoria categoria = umaCategoria(1L, "Açaí");
            ProdutoRequestDTO request = umRequest("Açaí 500ml",
                    new BigDecimal("10.00"), null, 1L, null);
            Produto salvo = umProduto(100L, "Açaí 500ml",
                    new BigDecimal("10.00"), null, categoria);

            when(produtoRepository.existsByNomeIgnoreCaseAndAtivoTrue("Açaí 500ml")).thenReturn(false);
            when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria));
            when(produtoRepository.save(any(Produto.class))).thenReturn(salvo);

            assertDoesNotThrow(() -> produtoService.criarProduto(request));
        }

        @Test
        @DisplayName("Deve criar produto com grupos modificadores vinculados — entidade persistida tem a relação")
        void deve_CriarProduto_quando_ComGruposModificadores() {
            Categoria categoria = umaCategoria(1L, "Açaí");
            GrupoModificador grupo = umGrupo(10L, "Frutas");
            ProdutoGrupoRequestDTO grupoReq = umGrupoRequest(10L, "MULTIPLA", 1, 3);
            ProdutoRequestDTO request = umRequest("Açaí 500ml",
                    new BigDecimal("15.50"), BigDecimal.ZERO, 1L, List.of(grupoReq));

            when(produtoRepository.existsByNomeIgnoreCaseAndAtivoTrue("Açaí 500ml")).thenReturn(false);
            when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria));
            when(grupoRepository.findById(10L)).thenReturn(Optional.of(grupo));
            when(produtoRepository.save(any(Produto.class))).thenAnswer(inv -> {
                Produto p = inv.getArgument(0);
                p.setId(100L);
                return p;
            });

            produtoService.criarProduto(request);

            ArgumentCaptor<Produto> captor = ArgumentCaptor.forClass(Produto.class);
            verify(produtoRepository).save(captor.capture());
            Produto persistido = captor.getValue();
            assertEquals("Açaí 500ml", persistido.getNome());
            assertEquals(categoria, persistido.getCategoria());
            assertEquals(1, persistido.getGruposModificadores().size());
            ProdutoGrupoModificador relacao = persistido.getGruposModificadores().iterator().next();
            assertEquals(10L, relacao.getGrupo().getId());
            assertEquals("MULTIPLA", relacao.getTipoEscolha());
            assertEquals(1, relacao.getMinOpcoes());
            assertEquals(3, relacao.getMaxOpcoes());
        }
    }

    // ─── listarProdutos ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("listarProdutos")
    class ListarProdutos {

        @Test
        @DisplayName("Deve retornar lista vazia quando não há produtos ativos")
        void deve_RetornarListaVazia_quando_NaoHaProdutosAtivos() {
            when(produtoRepository.findAllAtivosComGrupos()).thenReturn(List.of());

            List<ProdutoResponseDTO> resultado = produtoService.listarProdutos();

            assertTrue(resultado.isEmpty());
        }

        @Test
        @DisplayName("Deve retornar lista mapeada com margem bruta calculada")
        void deve_RetornarListaMapeada_quando_HaProdutosAtivos() {
            Categoria categoria = umaCategoria(1L, "Açaí");
            Produto p1 = umProduto(100L, "Açaí 500ml",
                    new BigDecimal("20.00"), new BigDecimal("8.00"), categoria);
            Produto p2 = umProduto(101L, "Smoothie",
                    new BigDecimal("15.00"), new BigDecimal("5.00"), categoria);

            when(produtoRepository.findAllAtivosComGrupos()).thenReturn(List.of(p1, p2));

            List<ProdutoResponseDTO> resultado = produtoService.listarProdutos();

            assertEquals(2, resultado.size());
            assertBD("60.00", resultado.get(0).margemBruta());
            assertBD("66.67", resultado.get(1).margemBruta());
        }

        @Test
        @DisplayName("Deve filtrar opções inativas no mapeamento das respostas")
        void deve_FiltrarOpcoesInativas_quando_MapeiaProdutoParaResponse() {
            Categoria categoria = umaCategoria(1L, "Açaí");
            OpcaoModificador ativa = umaOpcao(1L, "Banana", new BigDecimal("1.00"), true);
            OpcaoModificador inativa = umaOpcao(2L, "Kiwi (descontinuado)", new BigDecimal("2.00"), false);
            GrupoModificador grupo = umGrupoComOpcoes(10L, "Frutas", ativa, inativa);

            Produto produto = umProdutoComGrupo(100L, "Açaí 500ml",
                    new BigDecimal("20.00"), new BigDecimal("8.00"), categoria,
                    grupo, "MULTIPLA", 1, 3);

            when(produtoRepository.findAllAtivosComGrupos()).thenReturn(List.of(produto));

            List<ProdutoResponseDTO> resultado = produtoService.listarProdutos();

            assertEquals(1, resultado.get(0).gruposModificadores().get(0).opcoes().size());
            assertEquals("Banana", resultado.get(0).gruposModificadores().get(0).opcoes().get(0).nome());
        }
    }

    // ─── buscarPorId ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("buscarPorId")
    class BuscarPorId {

        @Test
        @DisplayName("Deve retornar produto mapeado quando id existe")
        void deve_RetornarProduto_quando_IdExiste() {
            Categoria categoria = umaCategoria(1L, "Açaí");
            Produto produto = umProduto(100L, "Açaí 500ml",
                    new BigDecimal("15.50"), new BigDecimal("5.00"), categoria);

            when(produtoRepository.findById(100L)).thenReturn(Optional.of(produto));

            ProdutoResponseDTO response = produtoService.buscarPorId(100L);

            assertEquals(100L, response.id());
            assertEquals("Açaí 500ml", response.nome());
        }

        @Test
        @DisplayName("Deve lançar ResourceNotFoundException quando produto não existe")
        void deve_LancarResourceNotFoundException_quando_ProdutoInexistente() {
            when(produtoRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> produtoService.buscarPorId(999L));

            verify(produtoRepository, never()).save(any(Produto.class));
        }
    }

    // ─── atualizarProduto ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("atualizarProduto")
    class AtualizarProduto {

        @Test
        @DisplayName("Deve atualizar campos do produto sem save explícito (dirty checking)")
        void deve_AtualizarProduto_quando_DadosValidos() {
            Categoria categoriaAntiga = umaCategoria(1L, "Antiga");
            Categoria categoriaNova = umaCategoria(2L, "Nova");
            Produto existente = umProduto(100L, "Nome Antigo",
                    new BigDecimal("10.00"), new BigDecimal("4.00"), categoriaAntiga);

            ProdutoRequestDTO request = umRequest("Nome Novo",
                    new BigDecimal("20.00"), new BigDecimal("8.00"), 2L, null);

            when(produtoRepository.findById(100L)).thenReturn(Optional.of(existente));
            when(produtoRepository.existsByNomeIgnoreCaseAndAtivoTrue("Nome Novo")).thenReturn(false);
            when(categoriaRepository.findById(2L)).thenReturn(Optional.of(categoriaNova));

            ProdutoResponseDTO response = produtoService.atualizarProduto(100L, request);

            assertEquals("Nome Novo", response.nome());
            assertBD("20.00", response.precoBase());
            assertBD("8.00", response.custoEstimado());
            assertEquals(2L, response.categoriaId());
            assertEquals("Nome Novo", existente.getNome());
            verify(produtoRepository, never()).save(any(Produto.class));
        }

        @Test
        @DisplayName("Deve lançar ResourceNotFoundException quando produto não existe")
        void deve_LancarResourceNotFoundException_quando_ProdutoInexistente() {
            ProdutoRequestDTO request = umRequestSimples("Qualquer", new BigDecimal("10"), 1L);
            when(produtoRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> produtoService.atualizarProduto(999L, request));

            verify(produtoRepository, never()).save(any(Produto.class));
        }

        @Test
        @DisplayName("Deve lançar BusinessException quando nome muda para um já existente em outro produto")
        void deve_LancarBusinessException_quando_NovoNomeJaExisteEmOutroProduto() {
            Categoria categoria = umaCategoria(1L, "Açaí");
            Produto existente = umProduto(100L, "Açaí 300ml",
                    new BigDecimal("10.00"), BigDecimal.ZERO, categoria);
            ProdutoRequestDTO request = umRequestSimples("Açaí 500ml",
                    new BigDecimal("15.50"), 1L);

            when(produtoRepository.findById(100L)).thenReturn(Optional.of(existente));
            when(produtoRepository.existsByNomeIgnoreCaseAndAtivoTrue("Açaí 500ml")).thenReturn(true);

            assertThrows(BusinessException.class,
                    () -> produtoService.atualizarProduto(100L, request));

            verify(categoriaRepository, never()).findById(anyLong());
            verify(produtoRepository, never()).save(any(Produto.class));
        }

        @Test
        @DisplayName("Não deve checar duplicidade quando novo nome difere apenas em caixa do nome atual")
        void deve_AtualizarSemChecarDuplicidade_quando_NomeMudaApenasEmCaixa() {
            Categoria categoria = umaCategoria(1L, "Açaí");
            Produto existente = umProduto(100L, "Açaí 500ml",
                    new BigDecimal("10.00"), BigDecimal.ZERO, categoria);
            ProdutoRequestDTO request = umRequestSimples("AÇAÍ 500ML",
                    new BigDecimal("15.50"), 1L);

            when(produtoRepository.findById(100L)).thenReturn(Optional.of(existente));
            when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria));

            assertDoesNotThrow(() -> produtoService.atualizarProduto(100L, request));

            assertEquals("AÇAÍ 500ML", existente.getNome());
            verify(produtoRepository, never()).existsByNomeIgnoreCaseAndAtivoTrue(anyString());
        }

        @Test
        @DisplayName("Deve lançar BusinessException quando custo estimado é maior que o preço base")
        void deve_LancarBusinessException_quando_CustoMaiorQuePrecoNaAtualizacao() {
            Categoria categoria = umaCategoria(1L, "Açaí");
            Produto existente = umProduto(100L, "Açaí 500ml",
                    new BigDecimal("10.00"), BigDecimal.ZERO, categoria);
            ProdutoRequestDTO request = umRequest("Açaí 500ml",
                    new BigDecimal("10.00"), new BigDecimal("15.00"), 1L, null);

            when(produtoRepository.findById(100L)).thenReturn(Optional.of(existente));

            assertThrows(BusinessException.class,
                    () -> produtoService.atualizarProduto(100L, request));

            verify(categoriaRepository, never()).findById(anyLong());
            verify(produtoRepository, never()).save(any(Produto.class));
        }

        @Test
        @DisplayName("Deve lançar ResourceNotFoundException quando categoria informada não existe")
        void deve_LancarResourceNotFoundException_quando_CategoriaInexistenteNaAtualizacao() {
            Categoria categoria = umaCategoria(1L, "Açaí");
            Produto existente = umProduto(100L, "Açaí 500ml",
                    new BigDecimal("10.00"), BigDecimal.ZERO, categoria);
            ProdutoRequestDTO request = umRequestSimples("Açaí 500ml",
                    new BigDecimal("15.50"), 999L);

            when(produtoRepository.findById(100L)).thenReturn(Optional.of(existente));
            when(categoriaRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> produtoService.atualizarProduto(100L, request));

            verify(produtoRepository, never()).save(any(Produto.class));
        }
    }

    // ─── inativarProduto ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("inativarProduto")
    class InativarProduto {

        @Test
        @DisplayName("Deve setar ativo=false confiando em dirty checking (sem save explícito)")
        void deve_InativarProduto_quando_ProdutoExiste() {
            Categoria categoria = umaCategoria(1L, "Açaí");
            Produto produto = umProduto(100L, "Açaí 500ml",
                    new BigDecimal("10.00"), BigDecimal.ZERO, categoria);

            when(produtoRepository.findById(100L)).thenReturn(Optional.of(produto));

            produtoService.inativarProduto(100L);

            assertFalse(produto.getAtivo());
            verify(produtoRepository, never()).save(any(Produto.class));
            verify(produtoRepository, never()).delete(any(Produto.class));
        }

        @Test
        @DisplayName("Deve lançar ResourceNotFoundException quando produto não existe")
        void deve_LancarResourceNotFoundException_quando_ProdutoInexistente() {
            when(produtoRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> produtoService.inativarProduto(999L));

            verify(produtoRepository, never()).save(any(Produto.class));
        }
    }

    // ─── sincronizarGruposNoProduto (via atualizarProduto) ────────────────────────

    @Nested
    @DisplayName("sincronizarGruposNoProduto")
    class SincronizarGrupos {

        @Test
        @DisplayName("Request com lista vazia deve limpar a coleção de grupos do produto")
        void deve_LimparGrupos_quando_RequestComListaVazia() {
            Categoria categoria = umaCategoria(1L, "Açaí");
            GrupoModificador grupo = umGrupo(10L, "Frutas");
            Produto existente = umProdutoComGrupo(100L, "Açaí 500ml",
                    new BigDecimal("10.00"), BigDecimal.ZERO, categoria,
                    grupo, "MULTIPLA", 1, 3);

            ProdutoRequestDTO request = umRequest("Açaí 500ml",
                    new BigDecimal("10.00"), BigDecimal.ZERO, 1L, List.of());

            when(produtoRepository.findById(100L)).thenReturn(Optional.of(existente));
            when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria));

            produtoService.atualizarProduto(100L, request);

            assertTrue(existente.getGruposModificadores().isEmpty());
        }

        @Test
        @DisplayName("Deve remover grupos que não estão no request")
        void deve_RemoverGrupos_quando_AusentesNoRequest() {
            Categoria categoria = umaCategoria(1L, "Açaí");
            GrupoModificador grupoA = umGrupo(10L, "Frutas");
            GrupoModificador grupoB = umGrupo(20L, "Caldas");
            Produto existente = umProduto(100L, "Açaí 500ml",
                    new BigDecimal("10.00"), BigDecimal.ZERO, categoria);
            anexarGrupoAoProduto(existente, grupoA, "MULTIPLA", 1, 3);
            anexarGrupoAoProduto(existente, grupoB, "UNICA", 1, 1);

            ProdutoRequestDTO request = umRequest("Açaí 500ml",
                    new BigDecimal("10.00"), BigDecimal.ZERO, 1L,
                    List.of(umGrupoRequest(10L, "MULTIPLA", 1, 3)));

            when(produtoRepository.findById(100L)).thenReturn(Optional.of(existente));
            when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria));

            produtoService.atualizarProduto(100L, request);

            assertEquals(1, existente.getGruposModificadores().size());
            assertEquals(10L,
                    existente.getGruposModificadores().iterator().next().getGrupo().getId());
            verify(grupoRepository, never()).findById(anyLong());
        }

        @Test
        @DisplayName("Deve atualizar tipoEscolha/min/max em relação já existente sem consultar repositório de grupos")
        void deve_AtualizarRelacaoExistente_quando_GrupoJaVinculado() {
            Categoria categoria = umaCategoria(1L, "Açaí");
            GrupoModificador grupo = umGrupo(10L, "Frutas");
            Produto existente = umProdutoComGrupo(100L, "Açaí 500ml",
                    new BigDecimal("10.00"), BigDecimal.ZERO, categoria,
                    grupo, "MULTIPLA", 1, 3);

            ProdutoRequestDTO request = umRequest("Açaí 500ml",
                    new BigDecimal("10.00"), BigDecimal.ZERO, 1L,
                    List.of(umGrupoRequest(10L, "UNICA", 0, 1)));

            when(produtoRepository.findById(100L)).thenReturn(Optional.of(existente));
            when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria));

            produtoService.atualizarProduto(100L, request);

            ProdutoGrupoModificador relacao = existente.getGruposModificadores().iterator().next();
            assertEquals("UNICA", relacao.getTipoEscolha());
            assertEquals(0, relacao.getMinOpcoes());
            assertEquals(1, relacao.getMaxOpcoes());
            verify(grupoRepository, never()).findById(anyLong());
        }

        @Test
        @DisplayName("Deve criar nova relação buscando o GrupoModificador no repositório")
        void deve_CriarNovaRelacao_quando_GrupoAindaNaoVinculado() {
            Categoria categoria = umaCategoria(1L, "Açaí");
            GrupoModificador grupoNovo = umGrupo(20L, "Caldas");
            Produto existente = umProduto(100L, "Açaí 500ml",
                    new BigDecimal("10.00"), BigDecimal.ZERO, categoria);

            ProdutoRequestDTO request = umRequest("Açaí 500ml",
                    new BigDecimal("10.00"), BigDecimal.ZERO, 1L,
                    List.of(umGrupoRequest(20L, "MULTIPLA", null, null)));

            when(produtoRepository.findById(100L)).thenReturn(Optional.of(existente));
            when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria));
            when(grupoRepository.findById(20L)).thenReturn(Optional.of(grupoNovo));

            produtoService.atualizarProduto(100L, request);

            assertEquals(1, existente.getGruposModificadores().size());
            ProdutoGrupoModificador nova = existente.getGruposModificadores().iterator().next();
            assertEquals(20L, nova.getGrupo().getId());
            assertEquals("MULTIPLA", nova.getTipoEscolha());
            assertEquals(0, nova.getMinOpcoes());
            assertEquals(1, nova.getMaxOpcoes());
            assertEquals(TENANT_ID, nova.getTenantId());
        }

        @Test
        @DisplayName("Deve lançar ResourceNotFoundException com ID interpolado quando grupo não existe")
        void deve_LancarResourceNotFoundException_quando_GrupoNovoInexistente() {
            Categoria categoria = umaCategoria(1L, "Açaí");
            Produto existente = umProduto(100L, "Açaí 500ml",
                    new BigDecimal("10.00"), BigDecimal.ZERO, categoria);

            ProdutoRequestDTO request = umRequest("Açaí 500ml",
                    new BigDecimal("10.00"), BigDecimal.ZERO, 1L,
                    List.of(umGrupoRequest(999L, "MULTIPLA", 1, 1)));

            when(produtoRepository.findById(100L)).thenReturn(Optional.of(existente));
            when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria));
            when(grupoRepository.findById(999L)).thenReturn(Optional.empty());

            ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                    () -> produtoService.atualizarProduto(100L, request));

            assertTrue(ex.getMessage().contains("999"),
                    () -> "esperava ID 999 interpolado na mensagem, foi: " + ex.getMessage());
            verify(produtoRepository, never()).save(any(Produto.class));
        }
    }

    // ─── mapToResponse (via buscarPorId) — cálculo de margem bruta ────────────────

    @Nested
    @DisplayName("Cálculo de margem bruta")
    class CalculoMargemBruta {

        @Test
        @DisplayName("Deve calcular margem bruta com scale=2 (HALF_UP) — preço 20.00 / custo 8.00 → 60.00%")
        void deve_CalcularMargemBruta_com_ScaleDois_quando_DadosValidos() {
            Categoria categoria = umaCategoria(1L, "Açaí");
            Produto produto = umProduto(100L, "Açaí 500ml",
                    new BigDecimal("20.00"), new BigDecimal("8.00"), categoria);

            when(produtoRepository.findById(100L)).thenReturn(Optional.of(produto));

            ProdutoResponseDTO response = produtoService.buscarPorId(100L);

            assertBD("60.00", response.margemBruta());
            assertEquals(2, response.margemBruta().scale());
        }

        @Test
        @DisplayName("Deve retornar margem zero quando preço base é nulo")
        void deve_RetornarMargemZero_quando_PrecoBaseNulo() {
            Categoria categoria = umaCategoria(1L, "Açaí");
            Produto produto = umProduto(100L, "Açaí 500ml",
                    null, new BigDecimal("5.00"), categoria);

            when(produtoRepository.findById(100L)).thenReturn(Optional.of(produto));

            ProdutoResponseDTO response = produtoService.buscarPorId(100L);

            assertBD("0", response.margemBruta());
        }

        @Test
        @DisplayName("Deve retornar margem zero quando preço base é zero")
        void deve_RetornarMargemZero_quando_PrecoBaseZero() {
            Categoria categoria = umaCategoria(1L, "Açaí");
            Produto produto = umProduto(100L, "Açaí 500ml",
                    BigDecimal.ZERO, new BigDecimal("5.00"), categoria);

            when(produtoRepository.findById(100L)).thenReturn(Optional.of(produto));

            ProdutoResponseDTO response = produtoService.buscarPorId(100L);

            assertBD("0", response.margemBruta());
        }

        @Test
        @DisplayName("Deve tratar custo nulo como zero — preço 10.00 / custo null → 100.00%")
        void deve_TratarCustoComoZero_quando_CustoEstimadoNulo() {
            Categoria categoria = umaCategoria(1L, "Açaí");
            Produto produto = umProduto(100L, "Açaí 500ml",
                    new BigDecimal("10.00"), null, categoria);

            when(produtoRepository.findById(100L)).thenReturn(Optional.of(produto));

            ProdutoResponseDTO response = produtoService.buscarPorId(100L);

            assertBD("100.00", response.margemBruta());
        }
    }

    // ─── uploadImagem ─────────────────────────────────────────────────────────────

    /**
     * Cobertura apenas dos caminhos de exceção. O happy-path toca Files.copy em
     * pasta "uploads/" relativa ao working dir — refactor candidato para teste de
     * integração com @TempDir.
     */
    @Nested
    @DisplayName("uploadImagem")
    class UploadImagem {

        @Test
        @DisplayName("Deve lançar BusinessException quando arquivo está vazio")
        void deve_LancarBusinessException_quando_ArquivoVazio() {
            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(true);

            assertThrows(BusinessException.class, () -> produtoService.uploadImagem(file));
        }

        @Test
        @DisplayName("Deve lançar BusinessException quando arquivo excede o tamanho máximo permitido")
        void deve_LancarBusinessException_quando_TamanhoExcedido() {
            ReflectionTestUtils.setField(produtoService, "maxSizeBytes", 1024L);
            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(false);
            when(file.getSize()).thenReturn(2048L);

            assertThrows(BusinessException.class, () -> produtoService.uploadImagem(file));
        }

        @Test
        @DisplayName("Deve lançar BusinessException quando contentType não é JPEG, PNG ou WebP")
        void deve_LancarBusinessException_quando_ContentTypeInvalido() {
            ReflectionTestUtils.setField(produtoService, "maxSizeBytes", 5_242_880L);
            MultipartFile file = mock(MultipartFile.class);
            when(file.isEmpty()).thenReturn(false);
            when(file.getSize()).thenReturn(1024L);
            when(file.getContentType()).thenReturn("application/pdf");

            assertThrows(BusinessException.class, () -> produtoService.uploadImagem(file));
        }

        /*
         * NOTA: os dois testes abaixo usam MockedStatic<Files> porque o método
         * referencia java.nio.file.Files diretamente (Paths.get("uploads"),
         * Files.exists, Files.createDirectories, Files.copy). Esse acoplamento
         * é incidental — quando o upload for refatorado para um ImagemStorage
         * injetável, estes dois testes podem ser descartados.
         */

        @Test
        @DisplayName("Deve criar diretório, copiar arquivo e retornar URL com baseUrl + UUID + extensão")
        void deve_RetornarUrl_quando_UploadComSucesso() throws IOException {
            ReflectionTestUtils.setField(produtoService, "uploadsBaseUrl", "http://test/uploads/");
            ReflectionTestUtils.setField(produtoService, "maxSizeBytes", 5_242_880L);

            MultipartFile file = mock(MultipartFile.class);
            InputStream stream = mock(InputStream.class);
            when(file.isEmpty()).thenReturn(false);
            when(file.getSize()).thenReturn(1024L);
            when(file.getContentType()).thenReturn("image/png");
            when(file.getInputStream()).thenReturn(stream);

            try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
                filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(false);
                filesMock.when(() -> Files.createDirectories(any(Path.class)))
                        .thenAnswer(inv -> inv.getArgument(0));
                filesMock.when(() -> Files.copy(any(InputStream.class), any(Path.class), any(CopyOption.class)))
                        .thenReturn(1024L);

                String url = produtoService.uploadImagem(file);

                assertTrue(url.startsWith("http://test/uploads/"),
                        () -> "URL deveria começar com baseUrl, foi: " + url);
                assertTrue(url.endsWith(".png"),
                        () -> "URL deveria terminar em .png, foi: " + url);
                filesMock.verify(() -> Files.createDirectories(any(Path.class)), times(1));
                filesMock.verify(
                        () -> Files.copy(any(InputStream.class), any(Path.class), any(CopyOption.class)),
                        times(1));
            }
        }

        @Test
        @DisplayName("Deve traduzir IOException do Files.copy em RuntimeException com mensagem amigável")
        void deve_LancarRuntimeException_quando_FilesCopyLancaIOException() throws IOException {
            ReflectionTestUtils.setField(produtoService, "uploadsBaseUrl", "http://test/uploads/");
            ReflectionTestUtils.setField(produtoService, "maxSizeBytes", 5_242_880L);

            MultipartFile file = mock(MultipartFile.class);
            InputStream stream = mock(InputStream.class);
            when(file.isEmpty()).thenReturn(false);
            when(file.getSize()).thenReturn(1024L);
            when(file.getContentType()).thenReturn("image/jpeg");
            when(file.getInputStream()).thenReturn(stream);

            try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
                filesMock.when(() -> Files.exists(any(Path.class))).thenReturn(true);
                filesMock.when(() -> Files.copy(any(InputStream.class), any(Path.class), any(CopyOption.class)))
                        .thenThrow(new IOException("disco cheio"));

                assertThrows(RuntimeException.class, () -> produtoService.uploadImagem(file));

                filesMock.verify(() -> Files.createDirectories(any(Path.class)), never());
            }
        }
    }
}
