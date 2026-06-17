package vexon.sellionpdv.categoria;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vexon.sellionpdv.categoria.dto.CategoriaRequestDTO;
import vexon.sellionpdv.categoria.dto.CategoriaResponseDTO;
import vexon.sellionpdv.common.exception.BusinessException;
import vexon.sellionpdv.common.exception.ResourceNotFoundException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;

    @Transactional
    public CategoriaResponseDTO criarCategoria(CategoriaRequestDTO request) {
        if (categoriaRepository.existsByNomeIgnoreCase(request.nome())) {
            throw new BusinessException("Já existe uma categoria cadastrada com esse nome.");
        }

        Categoria novaCategoria = Categoria.builder()
                .nome(request.nome())
                .build();

        Categoria categoriaSalva = categoriaRepository.save(novaCategoria);

        return new CategoriaResponseDTO(categoriaSalva.getId(), categoriaSalva.getNome());
    }

    @Transactional(readOnly = true)
    public List<CategoriaResponseDTO> listarCategorias() {
        return categoriaRepository.findAllByAtivoTrueOrderByIdAsc().stream()
                .map(cat -> new CategoriaResponseDTO(cat.getId(), cat.getNome()))
                .toList();
    }

    @Transactional
    public CategoriaResponseDTO atualizarCategoria(Long id, CategoriaRequestDTO request) {
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada."));

        if (!categoria.getNome().equalsIgnoreCase(request.nome()) &&
                categoriaRepository.existsByNomeIgnoreCase(request.nome())) {
            throw new BusinessException("Já existe outra categoria com este nome.");
        }

        categoria.setNome(request.nome());
        return mapToResponse(categoria);
    }

    @Transactional
    public void inativarCategoria(Long id) {
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada."));
        categoria.setAtivo(false);
    }

    private CategoriaResponseDTO mapToResponse(Categoria categoria) {
        return new CategoriaResponseDTO(categoria.getId(), categoria.getNome());
    }
}
