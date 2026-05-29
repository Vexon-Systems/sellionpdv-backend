package vexon.sellionpdv.categoria;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vexon.sellionpdv.categoria.dto.CategoriaRequestDTO;
import vexon.sellionpdv.categoria.dto.CategoriaResponseDTO;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoriaService {
    private final CategoriaRepository categoriaRepository;

    @Transactional
    public CategoriaResponseDTO criarCategoria(CategoriaRequestDTO request) {
        if(categoriaRepository.existsByNomeIgnoreCase(request.nome())){
            throw new RuntimeException("Já existe uma categoria cadastrada com esse nome");
        }

        Categoria novaCategoria = Categoria.builder()
                .nome(request.nome())
                .build();

        Categoria categoriaSalva = categoriaRepository.save(novaCategoria);

        return new CategoriaResponseDTO(categoriaSalva.getId(), categoriaSalva.getNome());
    }

    public List<CategoriaResponseDTO> listarCategorias() {
        return categoriaRepository.findAllByAtivoTrue().stream()
                .map(cat -> new CategoriaResponseDTO(cat.getId(), cat.getNome()))
                .toList();
    }

    @Transactional
    public CategoriaResponseDTO atualizarCategoria(Long id, CategoriaRequestDTO request) {
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Categoria não encontrada."));

        if (!categoria.getNome().equalsIgnoreCase(request.nome()) &&
                categoriaRepository.existsByNomeIgnoreCase(request.nome())) {
            throw new RuntimeException("Já existe outra categoria com este nome.");
        }

        categoria.setNome(request.nome());
        return mapToResponse(categoria);
    }

    @Transactional
    public void inativarCategoria(Long id) {
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Categoria não encontrada."));

        // Soft Delete
        categoria.setAtivo(false);
    }

    private CategoriaResponseDTO mapToResponse(Categoria categoria) {
        return new CategoriaResponseDTO(
                categoria.getId(),
                categoria.getNome()
        );
    }
}
