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
        return categoriaRepository.findAll().stream()
                .map(cat -> new CategoriaResponseDTO(cat.getId(), cat.getNome()))
                .toList();
    }
}
