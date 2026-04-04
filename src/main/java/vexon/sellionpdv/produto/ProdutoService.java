package vexon.sellionpdv.produto;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vexon.sellionpdv.categoria.Categoria;
import vexon.sellionpdv.categoria.CategoriaRepository;
import vexon.sellionpdv.produto.dto.ProdutoRequestDTO;
import vexon.sellionpdv.produto.dto.ProdutoResponseDTO;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final CategoriaRepository categoriaRepository;

    @Transactional
    public ProdutoResponseDTO criarProduto(ProdutoRequestDTO request) {
        if (produtoRepository.existsByNomeIgnoreCase(request.nome())) {
            throw new RuntimeException("Já existe um produto cadastrado com este nome.");
        }

        Categoria categoria = categoriaRepository.findById(request.categoriaId())
                .orElseThrow(() -> new RuntimeException("Categoria não encontrada."));

        Produto produto = Produto.builder()
                .nome(request.nome())
                .precoBase(request.precoBase())
                // Se o frontend não mandar custo estimado, salvamos como zero
                .custoEstimado(request.custoEstimado() != null ? request.custoEstimado() : BigDecimal.ZERO)
                .categoria(categoria)
                .build();

        Produto salvo = produtoRepository.save(produto);

        return new ProdutoResponseDTO(
                salvo.getId(),
                salvo.getNome(),
                salvo.getPrecoBase(),
                salvo.getCustoEstimado(),
                salvo.getCategoria().getId()
        );
    }
}