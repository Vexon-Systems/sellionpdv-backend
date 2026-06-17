package vexon.sellionpdv.produto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vexon.sellionpdv.categoria.Categoria;
import vexon.sellionpdv.categoria.CategoriaRepository;
import vexon.sellionpdv.produto.dto.ProdutoRequestDTO;
import vexon.sellionpdv.produto.dto.ProdutoResponseDTO;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProdutoServiceTest {

    @Mock
    private ProdutoRepository produtoRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @InjectMocks
    private ProdutoService produtoService;

    @Test
    @DisplayName("Deve criar um produto com sucesso quando todos os dados forem válidos")
    void deveCriarProdutoComSucesso() {
        // Arrange
        ProdutoRequestDTO request = new ProdutoRequestDTO(
                "Açaí 500ml", new BigDecimal("15.50"), BigDecimal.ZERO, true, 1L, null, null
        );

        Categoria categoriaSimulada = Categoria.builder().id(1L).nome("Açaí").build();
        Produto produtoSalvoSimulado = Produto.builder()
                .id(100L)
                .nome("Açaí 500ml")
                .precoBase(new BigDecimal("15.50"))
                .custoEstimado(BigDecimal.ZERO)
                .categoria(categoriaSimulada)
                .build();

        when(produtoRepository.existsByNomeIgnoreCaseAndAtivoTrue("Açaí 500ml")).thenReturn(false);
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoriaSimulada));
        when(produtoRepository.save(any(Produto.class))).thenReturn(produtoSalvoSimulado);

        // Act
        ProdutoResponseDTO response = produtoService.criarProduto(request);

        // Assert
        assertNotNull(response);
        assertEquals(100L, response.id());
        assertEquals("Açaí 500ml", response.nome());
        assertEquals(new BigDecimal("15.50"), response.precoBase());

        verify(produtoRepository, times(1)).save(any(Produto.class));
    }

    @Test
    @DisplayName("Deve lançar exceção ao tentar criar produto com nome já existente")
    void naoDeveCriarProdutoComNomeDuplicado() {
        // Arrange
        ProdutoRequestDTO request = new ProdutoRequestDTO(
                "Açaí 500ml", new BigDecimal("15.50"), BigDecimal.ZERO, true, 1L, null, null
        );

        when(produtoRepository.existsByNomeIgnoreCaseAndAtivoTrue("Açaí 500ml")).thenReturn(true);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            produtoService.criarProduto(request);
        });

        assertEquals("Já existe um produto cadastrado com este nome.", exception.getMessage());
        verify(produtoRepository, never()).save(any(Produto.class));
    }

    @Test
    @DisplayName("Deve lançar exceção ao tentar vincular a uma categoria inexistente")
    void naoDeveCriarProdutoComCategoriaInvalida() {
        // Arrange
        ProdutoRequestDTO request = new ProdutoRequestDTO(
                "Açaí 500ml", new BigDecimal("15.50"), BigDecimal.ZERO, true, 999L, null, null
        );

        when(produtoRepository.existsByNomeIgnoreCaseAndAtivoTrue("Açaí 500ml")).thenReturn(false);
        when(categoriaRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            produtoService.criarProduto(request);
        });

        assertEquals("Categoria não encontrada.", exception.getMessage());
        verify(produtoRepository, never()).save(any(Produto.class));
    }
}