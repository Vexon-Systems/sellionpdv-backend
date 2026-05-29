package vexon.sellionpdv.produto;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vexon.sellionpdv.categoria.Categoria;
import vexon.sellionpdv.categoria.CategoriaRepository;
import vexon.sellionpdv.modificador.GrupoModificador;
import vexon.sellionpdv.modificador.GrupoModificadorRepository;
import vexon.sellionpdv.produto.dto.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final CategoriaRepository categoriaRepository;

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
                .imagemUrl(request.imagemUrl())
                .build();

        sincronizarGruposNoProduto(produto, request.gruposModificadores());

        return mapToResponse(produtoRepository.save(produto));
    }

    public List<ProdutoResponseDTO> listarProdutos() {
        return produtoRepository.findAllByAtivoTrue().stream()
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
        produto.setImagemUrl(request.imagemUrl());

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
            produto.getGruposModificadores().clear();
            return;
        }

        List<Long> idsRecebidos = gruposRequest.stream()
                .map(ProdutoGrupoRequestDTO::grupoId)
                .toList();

        produto.getGruposModificadores().removeIf(relacao ->
                !idsRecebidos.contains(relacao.getGrupo().getId()));

        for (ProdutoGrupoRequestDTO dto : gruposRequest) {
            ProdutoGrupoModificador relacaoExistente = produto.getGruposModificadores().stream()
                    .filter(rel -> rel.getGrupo().getId().equals(dto.grupoId()))
                    .findFirst()
                    .orElse(null);

            if (relacaoExistente != null) {
                relacaoExistente.setTipoEscolha(dto.tipoEscolha());
                relacaoExistente.setMinOpcoes(dto.minOpcoes() != null ? dto.minOpcoes() : 0);
                relacaoExistente.setMaxOpcoes(dto.maxOpcoes() != null ? dto.maxOpcoes() : 1);
            } else {
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
        List<ProdutoGrupoResponseDTO> gruposDTO = produto.getGruposModificadores().stream()
                .map(relacao -> {
                    var grupo = relacao.getGrupo();

                    List<ProdutoOpcaoResponseDTO> opcoesDTO = grupo.getOpcoes().stream()
                            .filter(opcao -> opcao.getAtivo())
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
                produto.getImagemUrl(),
                gruposDTO
        );
    }

    public String uploadImagem(MultipartFile file) {
        try {
            if (file.isEmpty()) {
                throw new RuntimeException("Arquivo vazio.");
            }

            String fileName = UUID.randomUUID() + "-" + file.getOriginalFilename();

            Path uploadPath = Paths.get("uploads");

            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            Path filePath = uploadPath.resolve(fileName);

            Files.copy(
                    file.getInputStream(),
                    filePath,
                    StandardCopyOption.REPLACE_EXISTING
            );

            return "http://localhost:8080/uploads/" + fileName;

        } catch (Exception e) {
            throw new RuntimeException("Erro ao fazer upload da imagem.");
        }
    }
}