package vexon.sellionpdv.maquininha;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vexon.sellionpdv.common.exception.ResourceNotFoundException;
import vexon.sellionpdv.maquininha.dto.MaquininhaRequestDTO;
import vexon.sellionpdv.maquininha.dto.MaquininhaResponseDTO;
import vexon.sellionpdv.tenant.TenantContext;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MaquininhaService {

    private final MaquininhaRepository repository;

    @Transactional(readOnly = true)
    public List<MaquininhaResponseDTO> listarTodas() {
        return repository.findAll().stream()
                .map(MaquininhaResponseDTO::new)
                .toList();
    }

    @Transactional
    public MaquininhaResponseDTO cadastrar(MaquininhaRequestDTO dto) {
        Long tenantId = TenantContext.getCurrentTenant();

        Maquininha maquininha = Maquininha.builder()
                .tenantId(tenantId)
                .nome(dto.nome())
                .marca(dto.marca())
                .taxaDebito(dto.taxaDebito())
                .taxaCredito(dto.taxaCredito())
                .ativo(dto.ativo())
                .build();

        return new MaquininhaResponseDTO(repository.save(maquininha));
    }

    @Transactional
    public MaquininhaResponseDTO atualizar(Long id, MaquininhaRequestDTO dto) {
        Maquininha maquininha = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Maquininha não encontrada."));

        maquininha.setNome(dto.nome());
        maquininha.setMarca(dto.marca());
        maquininha.setTaxaDebito(dto.taxaDebito());
        maquininha.setTaxaCredito(dto.taxaCredito());
        maquininha.setAtivo(dto.ativo());

        return new MaquininhaResponseDTO(repository.save(maquininha));
    }

    @Transactional
    public void inativar(Long id) {
        Maquininha maquininha = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Maquininha não encontrada."));
        maquininha.setAtivo(false);
        repository.save(maquininha);
    }
}
