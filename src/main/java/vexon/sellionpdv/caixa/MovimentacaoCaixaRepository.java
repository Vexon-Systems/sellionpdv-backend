package vexon.sellionpdv.caixa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface MovimentacaoCaixaRepository
        extends JpaRepository<MovimentacaoCaixa, Long> {

    List<MovimentacaoCaixa> findByCaixa(
            Caixa caixa
    );

    @Query("SELECT m.tipo, SUM(m.valor) " +
            "FROM MovimentacaoCaixa m " +
            "WHERE m.dataMovimentacao >= :inicio AND m.dataMovimentacao <= :fim " +
            "GROUP BY m.tipo")
    List<Object[]> obterTotalMovimentacoesPorPeriodo(@Param("inicio") OffsetDateTime inicio, @Param("fim") OffsetDateTime fim);
}