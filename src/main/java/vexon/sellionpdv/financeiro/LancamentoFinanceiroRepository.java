package vexon.sellionpdv.financeiro;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface LancamentoFinanceiroRepository extends JpaRepository<LancamentoFinanceiro, Long> {

    List<LancamentoFinanceiro> findByDataReferenciaBetweenOrderByDataReferenciaDesc(LocalDate inicio, LocalDate fim);
}
