package vexon.sellionpdv.financeiro;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LancamentoFinanceiroRepository extends JpaRepository<LancamentoFinanceiro, Long> {

    List<LancamentoFinanceiro> findByDataReferenciaBetweenOrderByDataReferenciaDesc(LocalDate inicio, LocalDate fim);

    List<LancamentoFinanceiro> findByDataReferenciaBetweenAndStatusOrderByDataReferenciaDesc(
            LocalDate inicio, LocalDate fim, StatusLancamentoFinanceiro status);

    Optional<LancamentoFinanceiro> findByIdAndTenantId(Long id, Long tenantId);

    Optional<LancamentoFinanceiro> findByTenantIdAndIdempotencyKey(Long tenantId, UUID idempotencyKey);
}
