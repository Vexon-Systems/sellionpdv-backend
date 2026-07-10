package vexon.sellionpdv.caixa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface CaixaRepository extends JpaRepository<Caixa, Long> {

    // JOIN FETCH em operadorAbertura para permitir acesso a caixa.getOperadorAbertura().getNome()
    // fora da transação (usado no CaixaController → CaixaResponseDTO).
    @Query("SELECT c FROM Caixa c JOIN FETCH c.operadorAbertura WHERE c.status = :status")
    Optional<Caixa> findByStatus(@Param("status") StatusCaixa status);

    @Query("SELECT COUNT(c.id), AVG(c.saldoInicial), SUM(c.furoCaixa) " +
            "FROM Caixa c " +
            "WHERE c.dataAbertura >= :inicio AND c.dataAbertura <= :fim")
    Object[][] obterDadosResumidosCaixa(@Param("inicio") OffsetDateTime inicio, @Param("fim") OffsetDateTime fim);
}