package vexon.sellionpdv.financeiro;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vexon.sellionpdv.common.exception.ResourceNotFoundException;
import vexon.sellionpdv.financeiro.dto.LancamentoRequestDTO;
import vexon.sellionpdv.financeiro.dto.LancamentoResponseDTO;
import vexon.sellionpdv.tenant.TenantContext;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LancamentoFinanceiroService {

    private final LancamentoFinanceiroRepository repository;

    @Transactional(readOnly = true)
    public List<LancamentoResponseDTO> listarPorPeriodo(LocalDate dataInicial, LocalDate dataFinal) {
        return repository.findByDataReferenciaBetweenOrderByDataReferenciaDesc(dataInicial, dataFinal)
                .stream()
                .map(LancamentoResponseDTO::new)
                .toList();
    }

    @Transactional
    public LancamentoResponseDTO criar(LancamentoRequestDTO dto) {
        LancamentoFinanceiro lancamento = LancamentoFinanceiro.builder()
                .tenantId(TenantContext.getCurrentTenant())
                .descricao(dto.descricao())
                .valor(dto.valor())
                .categoria(dto.categoria())
                .dataReferencia(dto.dataReferencia())
                .build();

        return new LancamentoResponseDTO(repository.save(lancamento));
    }

    @Transactional
    public LancamentoResponseDTO atualizar(Long id, LancamentoRequestDTO dto) {
        LancamentoFinanceiro lancamento = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lançamento não encontrado."));

        lancamento.setDescricao(dto.descricao());
        lancamento.setValor(dto.valor());
        lancamento.setCategoria(dto.categoria());
        lancamento.setDataReferencia(dto.dataReferencia());

        return new LancamentoResponseDTO(repository.save(lancamento));
    }

    @Transactional
    public void excluir(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Lançamento não encontrado.");
        }
        repository.deleteById(id);
    }
}
