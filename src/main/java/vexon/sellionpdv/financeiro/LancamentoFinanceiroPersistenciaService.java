package vexon.sellionpdv.financeiro;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class LancamentoFinanceiroPersistenciaService {

    private final LancamentoFinanceiroRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LancamentoFinanceiro persistir(LancamentoFinanceiro lancamento) {
        return repository.saveAndFlush(lancamento);
    }
}
