package vexon.sellionpdv.categoria;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vexon.sellionpdv.categoria.dto.CategoriaRequestDTO;
import vexon.sellionpdv.categoria.dto.CategoriaResponseDTO;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoriaServiceTest {

    @Mock
    private CategoriaRepository categoriaRepository;

    @InjectMocks
    private CategoriaService categoriaService;

    @Test
    @DisplayName("Deve criar uma categoria com sucesso quando o nome não existir")
    void deveCriarCategoriaComSucesso() {

        CategoriaRequestDTO request = new CategoriaRequestDTO("Sorvetes de Massa");

        when(categoriaRepository.existsByNomeIgnoreCase("Sorvetes de Massa")).thenReturn(false);

        Categoria categoriaSimulada = Categoria.builder().id(1L).nome("Sorvetes de Massa").build();
        when(categoriaRepository.save(any(Categoria.class))).thenReturn(categoriaSimulada);

        CategoriaResponseDTO response = categoriaService.criarCategoria(request);

        // Verificação
        assertNotNull(response);
        assertEquals(1L, response.id());
        assertEquals("Sorvetes de Massa", response.nome());

        // Verifica se o método save do banco de dados falso foi realmente chamado exatamente 1 vez
        verify(categoriaRepository, times(1)).save(any(Categoria.class));
    }

    @Test
    @DisplayName("Deve lançar exceção ao tentar criar categoria com nome duplicado")
    void naoDeveCriarCategoriaComNomeDuplicado() {
        CategoriaRequestDTO request = new CategoriaRequestDTO("Picolés");

        when(categoriaRepository.existsByNomeIgnoreCase("Picolés")).thenReturn(true);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            categoriaService.criarCategoria(request);
        });

        assertEquals("Já existe uma categoria cadastrada com esse nome", exception.getMessage());

        // Segurança: Verifica se o método save NUNCA foi chamado, prevenindo que suje o banco
        verify(categoriaRepository, never()).save(any(Categoria.class));
    }
}