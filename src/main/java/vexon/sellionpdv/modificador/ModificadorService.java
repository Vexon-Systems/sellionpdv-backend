package vexon.sellionpdv.modificador;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
        if(grupoRepository.existsByNomeIgnoreCase(request.nome())){
            throw new RuntimeException("Já existe um grupo de modificadores com esse nome.");
        }

        GrupoModificador grupo = GrupoModificador.builder()
                .nome(request.nome())
                .build();

        request.opcoes().forEach(optDto -> {
            OpcaoModificador opcao = OpcaoModificador.builder()
                    .nome(optDto.nome())
                    .precoAdicional(optDto.precoAdicional())
                    .build();
            grupo.adicionarOpcao(opcao);
        });

        GrupoModificador salvo = grupoRepository.save(grupo);

        return mapToResponse(salvo);
    }

    public List<GrupoResponseDTO> listarGrupos() {
        return grupoRepository.findAllByAtivoTrue().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public GrupoResponseDTO atualizarGrupo(Long id, GrupoRequestDTO request) {
        GrupoModificador grupo = grupoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Grupo de modificadores não encontrado."));

        if (!grupo.getNome().equalsIgnoreCase(request.nome()) &&
                grupoRepository.existsByNomeIgnoreCase(request.nome())) {
            throw new RuntimeException("Já existe um grupo de modificadores com este nome.");
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
                opcaoExistente.setAtivo(true);
            } else {
                OpcaoModificador novaOpcao = OpcaoModificador.builder()
                        .nome(opRequest.nome())
                        .precoAdicional(opRequest.precoAdicional())
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
                .orElseThrow(() -> new RuntimeException("Grupo de modificadores não encontrado."));

        List<Produto> produtosAfetados = produtoRepository.findByGruposModificadoresId(id);

        produtosAfetados.forEach(produto -> {
            produto.getGruposModificadores().remove(grupo);
        });

        produtoRepository.saveAll(produtosAfetados);

        grupo.setAtivo(false);
        grupo.getOpcoes().forEach(op -> op.setAtivo(false));

        grupoRepository.save(grupo);
    }


    private GrupoResponseDTO mapToResponse(GrupoModificador grupo) {
        List<OpcaoResponseDTO> opcoesDto = grupo.getOpcoes().stream()
                .map(o -> new OpcaoResponseDTO(o.getId(), o.getNome(), o.getPrecoAdicional()))
                .toList();
        return new GrupoResponseDTO(grupo.getId(), grupo.getNome(), opcoesDto);
    }
}
