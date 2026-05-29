package vexon.sellionpdv.relatorio;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vexon.sellionpdv.caixa.Caixa; // Certifique-se de que o import aponta para sua entidade Caixa correta
import vexon.sellionpdv.relatorio.dto.RelatorioCaixaDTO;

import java.time.OffsetDateTime;

public interface RelatorioCaixaRepository extends JpaRepository<Caixa, Long> {

    @Query("""
        SELECT new vexon.sellionpdv.relatorio.dto.RelatorioCaixaDTO(
            c.id,
            cast(c.status as string),
            opAbertura.nome,
            opFechamento.nome,
            c.dataAbertura,
            c.dataFechamento,
            c.saldoInicial,
            (SELECT SUM(v.totalFinal) FROM Venda v WHERE v.caixa.id = c.id AND v.status = 'CONCLUIDA' AND v.formaPagamento = 'DINHEIRO'),
            (SELECT SUM(m.valor) FROM MovimentacaoCaixa m WHERE m.caixa.id = c.id AND m.tipo = 'SANGRIA'),
            (SELECT SUM(m.valor) FROM MovimentacaoCaixa m WHERE m.caixa.id = c.id AND m.tipo = 'REFORCO'),
            c.saldoFinalInformado,
            c.furoCaixa
        )
        FROM Caixa c
        LEFT JOIN c.operadorAbertura opAbertura
        LEFT JOIN c.operadorFechamento opFechamento
        WHERE c.dataAbertura >= :inicio AND c.dataAbertura < :fim
        ORDER BY c.dataAbertura DESC
    """)
    Page<RelatorioCaixaDTO> findCaixasByPeriodo(
            @Param("inicio") OffsetDateTime inicio,
            @Param("fim") OffsetDateTime fim,
            Pageable pageable
    );
}