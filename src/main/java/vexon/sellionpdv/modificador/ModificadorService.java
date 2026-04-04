package vexon.sellionpdv.modificador;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vexon.sellionpdv.modificador.dto.*;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ModificadorService {

    private final GrupoModificadorRepository grupoRepository;

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
        return grupoRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    private GrupoResponseDTO mapToResponse(GrupoModificador grupo) {
        List<OpcaoResponseDTO> opcoesDto = grupo.getOpcoes().stream()
                .map(o -> new OpcaoResponseDTO(o.getId(), o.getNome(), o.getPrecoAdicional()))
                .toList();
        return new GrupoResponseDTO(grupo.getId(), grupo.getNome(), opcoesDto);
    }
}
