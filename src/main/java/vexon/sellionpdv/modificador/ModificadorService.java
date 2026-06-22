package vexon.sellionpdv.modificador;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vexon.sellionpdv.common.exception.BusinessException;
import vexon.sellionpdv.common.exception.ResourceNotFoundException;
import vexon.sellionpdv.modificador.dto.*;
import vexon.sellionpdv.produto.Produto;
import vexon.sellionpdv.produto.ProdutoRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ModificadorService {

    private final GrupoModificadorRepository grupoRepository;
    private final ProdutoRepository produtoRepository;

    @Transactional
    public GrupoResponseDTO criarGrupo(GrupoRequestDTO request) {
        if (grupoRepository.existsByNomeIgnoreCaseAndAtivoTrue(request.nome())) {
            throw new BusinessException("Já existe um grupo de modificadores com esse nome.");
        }

        GrupoModificador grupo = GrupoModificador.builder()
                .nome(request.nome())
                .build();

        request.opcoes().forEach(optDto -> {
            OpcaoModificador opcao = OpcaoModificador.builder()
                    .nome(optDto.nome())
                    .precoAdicional(optDto.precoAdicional())
                    .custoEstimado(optDto.custoEstimado())
                    .build();
            grupo.adicionarOpcao(opcao);
        });

        return mapToResponse(grupoRepository.save(grupo));
    }

    @Transactional(readOnly = true)
    public List<GrupoResponseDTO> listarGrupos() {
        return grupoRepository.findAllByAtivoTrue().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public GrupoResponseDTO atualizarGrupo(Long id, GrupoRequestDTO request) {
        GrupoModificador grupo = grupoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo de modificadores não encontrado."));

        if (!grupo.getNome().equalsIgnoreCase(request.nome()) &&
                grupoRepository.existsByNomeIgnoreCaseAndAtivoTrue(request.nome())) {
            throw new BusinessException("Já existe um grupo de modificadores com este nome.");
        }

        grupo.setNome(request.nome());
        grupo.getOpcoes().forEach(op -> op.setAtivo(false));

        for (OpcaoRequestDTO opRequest : request.opcoes()) {
            OpcaoModificador opcaoExistente = grupo.getOpcoes().stream()
                    .filter(op -> op.getNome().equalsIgnoreCase(opRequest.nome()))
                    .findFirst()
                    .orElse(null);

            if (opcaoExistente != null) {
                opcaoExistente.setPrecoAdicional(opRequest.precoAdicional());
                opcaoExistente.setCustoEstimado(opRequest.custoEstimado());
                opcaoExistente.setAtivo(true);
            } else {
                OpcaoModificador novaOpcao = OpcaoModificador.builder()
                        .nome(opRequest.nome())
                        .precoAdicional(opRequest.precoAdicional())
                        .custoEstimado(opRequest.custoEstimado())
                        .grupo(grupo)
                        .ativo(true)
                        .build();
                grupo.getOpcoes().add(novaOpcao);
            }
        }

        return mapToResponse(grupoRepository.save(grupo));
    }

    @Transactional
    public void deletarGrupo(Long id) {
        GrupoModificador grupo = grupoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo de modificadores não encontrado."));

        List<Produto> produtosAfetados = produtoRepository.findByGrupoModificadorId(id);
        produtosAfetados.forEach(produto ->
                produto.getGruposModificadores().removeIf(pgm -> pgm.getGrupo().getId().equals(id)));
        produtoRepository.saveAll(produtosAfetados);

        grupo.setAtivo(false);
        grupo.getOpcoes().forEach(op -> op.setAtivo(false));
        grupoRepository.save(grupo);
    }

    private GrupoResponseDTO mapToResponse(GrupoModificador grupo) {
        List<OpcaoResponseDTO> opcoesDto = grupo.getOpcoes().stream()
                .filter(o -> Boolean.TRUE.equals(o.getAtivo()))
                .map(o -> new OpcaoResponseDTO(o.getId(), o.getNome(), o.getPrecoAdicional(), o.getCustoEstimado()))
                .toList();
        return new GrupoResponseDTO(grupo.getId(), grupo.getNome(), opcoesDto);
    }
}
