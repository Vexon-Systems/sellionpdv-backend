package vexon.sellionpdv.produto;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vexon.sellionpdv.categoria.Categoria;
import vexon.sellionpdv.categoria.CategoriaRepository;
import vexon.sellionpdv.modificador.GrupoModificador;
import vexon.sellionpdv.modificador.GrupoModificadorRepository;
import vexon.sellionpdv.produto.dto.*;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final CategoriaRepository categoriaRepository;

    // NOVO: Adicionado para conseguirmos buscar os modificadores no banco
    private final GrupoModificadorRepository grupoRepository;

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

        // Passa a lista inicial para o motor criar as relações
        sincronizarGruposNoProduto(produto, request.gruposModificadores());

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

        // Atualiza os dados base
        produto.setNome(request.nome());
        produto.setPrecoBase(request.precoBase());
        produto.setAtivo(request.ativo());
        produto.setCategoria(categoria);

        // Em vez de usar o clear nós usamos a sincronização
        sincronizarGruposNoProduto(produto, request.gruposModificadores());

        return mapToResponse(produto);
    }

    @Transactional
    public void inativarProduto(Long id) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado."));

        // Soft Delete
        produto.setAtivo(false);
    }

    private void sincronizarGruposNoProduto(Produto produto, List<ProdutoGrupoRequestDTO> gruposRequest) {
        if (gruposRequest == null || gruposRequest.isEmpty()) {
            // Se o frontend mandou vazio, apagamos tudo de forma segura
            produto.getGruposModificadores().clear();
            return;
        }

        // 1 - Extrair os IDs que vieram do Frontend
        List<Long> idsRecebidos = gruposRequest.stream()
                .map(ProdutoGrupoRequestDTO::grupoId)
                .toList();

        // 2 - REMOVER: Tirar da lista as relações que não vieram no JSON
        produto.getGruposModificadores().removeIf(relacao ->
                !idsRecebidos.contains(relacao.getGrupo().getId()));

        // 3 - ATUALIZAR ou ADICIONAR
        for (ProdutoGrupoRequestDTO dto : gruposRequest) {
            // Procura se essa relação já existe na memória do Hibernate
            ProdutoGrupoModificador relacaoExistente = produto.getGruposModificadores().stream()
                    .filter(rel -> rel.getGrupo().getId().equals(dto.grupoId()))
                    .findFirst()
                    .orElse(null);

            if (relacaoExistente != null) {
                // ATUALIZAR
                relacaoExistente.setTipoEscolha(dto.tipoEscolha());
                relacaoExistente.setMinOpcoes(dto.minOpcoes() != null ? dto.minOpcoes() : 0);
                relacaoExistente.setMaxOpcoes(dto.maxOpcoes() != null ? dto.maxOpcoes() : 1);
            } else {
                // ADICIONAR NOVA RELAÇÃO
                GrupoModificador grupo = grupoRepository.findById(dto.grupoId())
                        .orElseThrow(() -> new RuntimeException("Grupo não encontrado: " + dto.grupoId()));

                ProdutoGrupoModificador novaRelacao = ProdutoGrupoModificador.builder()
                        .id(new ProdutoGrupoId())
                        .produto(produto)
                        .grupo(grupo)
                        .tipoEscolha(dto.tipoEscolha())
                        .minOpcoes(dto.minOpcoes() != null ? dto.minOpcoes() : 0)
                        .maxOpcoes(dto.maxOpcoes() != null ? dto.maxOpcoes() : 1)
                        .tenantId(grupo.getTenantId())
                        .build();

                produto.getGruposModificadores().add(novaRelacao);
            }
        }
    }

    private ProdutoResponseDTO mapToResponse(Produto produto) {
        // Constrói a árvore de Modificadores para enviar ao Frontend
        List<ProdutoGrupoResponseDTO> gruposDTO = produto.getGruposModificadores().stream()
                .map(relacao -> {
                    var grupo = relacao.getGrupo();

                    List<ProdutoOpcaoResponseDTO> opcoesDTO = grupo.getOpcoes().stream()
                            .filter(opcao -> opcao.getAtivo()) // Oculta opções apagadas
                            .map(opcao -> new ProdutoOpcaoResponseDTO(
                                    opcao.getId(),
                                    opcao.getNome(),
                                    opcao.getPrecoAdicional()
                            ))
                            .toList();

                    return new ProdutoGrupoResponseDTO(
                            grupo.getId(),
                            grupo.getNome(),
                            relacao.getTipoEscolha(),
                            relacao.getMinOpcoes(),
                            relacao.getMaxOpcoes(),
                            opcoesDTO
                    );
                })
                .toList();

        return new ProdutoResponseDTO(
                produto.getId(),
                produto.getNome(),
                produto.getPrecoBase(),
                produto.getAtivo(),
                produto.getCategoria().getId(),
                gruposDTO
        );
    }
}