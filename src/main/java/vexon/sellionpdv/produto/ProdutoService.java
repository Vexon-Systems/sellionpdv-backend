package vexon.sellionpdv.produto;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vexon.sellionpdv.categoria.Categoria;
import vexon.sellionpdv.categoria.CategoriaRepository;
import vexon.sellionpdv.common.exception.BusinessException;
import vexon.sellionpdv.common.exception.ResourceNotFoundException;
import vexon.sellionpdv.modificador.GrupoModificador;
import vexon.sellionpdv.modificador.GrupoModificadorRepository;
import vexon.sellionpdv.produto.dto.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProdutoService {

    private static final Map<String, String> MIME_PARA_EXTENSAO = Map.of(
            "image/jpeg", ".jpg",
            "image/png",  ".png",
            "image/webp", ".webp"
    );

    private final ProdutoRepository produtoRepository;
    private final CategoriaRepository categoriaRepository;
    private final GrupoModificadorRepository grupoRepository;

    @Value("${app.uploads.base-url}")
    private String uploadsBaseUrl;

    @Value("${app.uploads.max-size-bytes}")
    private long maxSizeBytes;

    @Transactional
    public ProdutoResponseDTO criarProduto(ProdutoRequestDTO request) {
        if (produtoRepository.existsByNomeIgnoreCase(request.nome())) {
            throw new BusinessException("Já existe um produto cadastrado com este nome.");
        }

        Categoria categoria = categoriaRepository.findById(request.categoriaId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada."));

        Produto produto = Produto.builder()
                .nome(request.nome())
                .precoBase(request.precoBase())
                .custoEstimado(request.custoEstimado())
                .ativo(request.ativo())
                .categoria(categoria)
                .imagemUrl(request.imagemUrl())
                .build();

        sincronizarGruposNoProduto(produto, request.gruposModificadores());

        return mapToResponse(produtoRepository.save(produto));
    }

    @Transactional(readOnly = true)
    public List<ProdutoResponseDTO> listarProdutos() {
        return produtoRepository.findAllAtivosComGrupos().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProdutoResponseDTO buscarPorId(Long id) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado."));
        return mapToResponse(produto);
    }

    @Transactional
    public ProdutoResponseDTO atualizarProduto(Long id, ProdutoRequestDTO request) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado."));

        if (!produto.getNome().equalsIgnoreCase(request.nome()) &&
                produtoRepository.existsByNomeIgnoreCase(request.nome())) {
            throw new BusinessException("Já existe outro produto cadastrado com este nome.");
        }

        Categoria categoria = categoriaRepository.findById(request.categoriaId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada."));

        produto.setNome(request.nome());
        produto.setPrecoBase(request.precoBase());
        produto.setCustoEstimado(request.custoEstimado());
        produto.setAtivo(request.ativo());
        produto.setCategoria(categoria);
        produto.setImagemUrl(request.imagemUrl());

        sincronizarGruposNoProduto(produto, request.gruposModificadores());

        return mapToResponse(produto);
    }

    @Transactional
    public void inativarProduto(Long id) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado."));
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
                        .orElseThrow(() -> new ResourceNotFoundException("Grupo não encontrado: " + dto.grupoId()));

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

        BigDecimal custo = produto.getCustoEstimado() != null ? produto.getCustoEstimado() : BigDecimal.ZERO;
        BigDecimal margemBruta = BigDecimal.ZERO;

        if (produto.getPrecoBase() != null && produto.getPrecoBase().compareTo(BigDecimal.ZERO) > 0) {
            margemBruta = produto.getPrecoBase()
                    .subtract(custo)
                    .divide(produto.getPrecoBase(), 4, java.math.RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
        }

        return new ProdutoResponseDTO(
                produto.getId(),
                produto.getNome(),
                produto.getPrecoBase(),
                produto.getCustoEstimado(),
                margemBruta,
                produto.getAtivo(),
                produto.getCategoria().getId(),
                produto.getImagemUrl(),
                gruposDTO
        );
    }

    public String uploadImagem(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException("Arquivo vazio.");
        }

        if (file.getSize() > maxSizeBytes) {
            throw new BusinessException("Arquivo excede o tamanho máximo permitido de 5 MB.");
        }

        String contentType = file.getContentType();
        String extensao = MIME_PARA_EXTENSAO.get(contentType);
        if (extensao == null) {
            throw new BusinessException("Tipo de arquivo não permitido. Envie uma imagem JPEG, PNG ou WebP.");
        }

        try {
            String nomeArquivo = UUID.randomUUID() + extensao;

            Path uploadPath = Paths.get("uploads");
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            Files.copy(file.getInputStream(), uploadPath.resolve(nomeArquivo), StandardCopyOption.REPLACE_EXISTING);

            return uploadsBaseUrl + nomeArquivo;

        } catch (Exception e) {
            throw new RuntimeException("Erro ao salvar a imagem. Tente novamente.");
        }
    }
}
