package vexon.sellionpdv.produto;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vexon.sellionpdv.categoria.Categoria;
import vexon.sellionpdv.categoria.CategoriaRepository;
import vexon.sellionpdv.produto.dto.ProdutoRequestDTO;
import vexon.sellionpdv.produto.dto.ProdutoResponseDTO;

import java.util.List;

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
                .ativo(request.ativo())
                .categoria(categoria)
                .build();

        return mapToResponse(produtoRepository.save(produto));
    }

    public List<ProdutoResponseDTO> listarProdutos() {
        return produtoRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    public ProdutoResponseDTO buscarPorId(Long id) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado."));
        return mapToResponse(produto);
    }

    @Transactional
    public ProdutoResponseDTO atualizarProduto(Long id, ProdutoRequestDTO request) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado."));

        if (!produto.getNome().equalsIgnoreCase(request.nome()) &&
                produtoRepository.existsByNomeIgnoreCase(request.nome())) {
            throw new RuntimeException("Já existe outro produto cadastrado com este nome.");
        }

        Categoria categoria = categoriaRepository.findById(request.categoriaId())
                .orElseThrow(() -> new RuntimeException("Categoria não encontrada."));

        produto.setNome(request.nome());
        produto.setPrecoBase(request.precoBase());
        produto.setAtivo(request.ativo());
        produto.setCategoria(categoria);

        return mapToResponse(produto);
    }

    @Transactional
    public void inativarProduto(Long id) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado."));

        produto.setAtivo(false);
    }

    private ProdutoResponseDTO mapToResponse(Produto produto) {
        return new ProdutoResponseDTO(
                produto.getId(),
                produto.getNome(),
                produto.getPrecoBase(),
                produto.getAtivo(),
                produto.getCategoria().getId()
        );
    }
}