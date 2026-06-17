package vexon.sellionpdv.venda;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vexon.sellionpdv.caixa.Caixa;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VendaRepository extends JpaRepository<Venda, Long> {

    List<Venda> findByCaixa(
            Caixa caixa
    );

    Optional<Venda> findByIdempotencyKey(
            UUID idempotencyKey
    );

    // Busca os KPIs Totais
    @Query("SELECT SUM(v.totalFinal), COUNT(v.id) " +
            "FROM Venda v " +
            "WHERE v.status = 'CONCLUIDA' " +
            "AND v.dataVenda >= :inicio AND v.dataVenda <= :fim")
    Object[][] obterKpisDeVendas(@Param("inicio") OffsetDateTime inicio, @Param("fim") OffsetDateTime fim);

    // Agrupa por Forma de Pagamento
    @Query("SELECT v.formaPagamento, SUM(v.totalFinal), COUNT(v.id) " +
            "FROM Venda v " +
            "WHERE v.status = 'CONCLUIDA' " +
            "AND v.dataVenda >= :inicio AND v.dataVenda <= :fim " +
            "GROUP BY v.formaPagamento")
    List<Object[]> agruparFaturamentoPorPagamento(@Param("inicio") OffsetDateTime inicio, @Param("fim") OffsetDateTime fim);

    // Produtos mais vendidos no período
    @Query("SELECT iv.produto.id, iv.produto.nome, SUM(iv.quantidade), SUM(iv.subtotalItem) " +
            "FROM ItemVenda iv " +
            "WHERE iv.venda.status = 'CONCLUIDA' " +
            "AND iv.venda.dataVenda >= :inicio AND iv.venda.dataVenda <= :fim " +
            "GROUP BY iv.produto.id, iv.produto.nome " +
            "ORDER BY SUM(iv.quantidade) DESC")
    List<Object[]> obterProdutosTop(@Param("inicio") OffsetDateTime inicio, @Param("fim") OffsetDateTime fim);

    // Faturamento agrupado por Categorias
    @Query("SELECT iv.produto.categoria.id, iv.produto.categoria.nome, SUM(iv.quantidade), SUM(iv.subtotalItem) " +
            "FROM ItemVenda iv " +
            "WHERE iv.venda.status = 'CONCLUIDA' " +
            "AND iv.venda.dataVenda >= :inicio AND iv.venda.dataVenda <= :fim " +
            "GROUP BY iv.produto.categoria.id, iv.produto.categoria.nome")
    List<Object[]> obterFaturamentoPorCategoria(@Param("inicio") OffsetDateTime inicio, @Param("fim") OffsetDateTime fim);

    // Série Temporal - Agrupamento por Hora
    @Query("SELECT FUNCTION('to_char', FUNCTION('timezone', 'America/Sao_Paulo', v.dataVenda), 'HH24:00') as periodo, " +
            "SUM(v.totalFinal) as total " +
            "FROM Venda v " +
            "WHERE v.status = 'CONCLUIDA' " +
            "AND v.dataVenda >= :inicio AND v.dataVenda <= :fim " +
            "GROUP BY FUNCTION('to_char', FUNCTION('timezone', 'America/Sao_Paulo', v.dataVenda), 'HH24:00') " +
            "ORDER BY FUNCTION('to_char', FUNCTION('timezone', 'America/Sao_Paulo', v.dataVenda), 'HH24:00')")
    List<Object[]> obterSerieTemporalPorHora(@Param("inicio") OffsetDateTime inicio, @Param("fim") OffsetDateTime fim);

    // Série Temporal - Agrupamento por Dia
    @Query("SELECT FUNCTION('to_char', FUNCTION('timezone', 'America/Sao_Paulo', v.dataVenda), 'DD/MM/YYYY') as periodo, " +
            "SUM(v.totalFinal) as total " +
            "FROM Venda v " +
            "WHERE v.status = 'CONCLUIDA' " +
            "AND v.dataVenda >= :inicio AND v.dataVenda <= :fim " +
            "GROUP BY FUNCTION('to_char', FUNCTION('timezone', 'America/Sao_Paulo', v.dataVenda), 'DD/MM/YYYY') " +
            "ORDER BY FUNCTION('to_char', FUNCTION('timezone', 'America/Sao_Paulo', v.dataVenda), 'DD/MM/YYYY')")
    List<Object[]> obterSerieTemporalPorDia(@Param("inicio") OffsetDateTime inicio, @Param("fim") OffsetDateTime fim);

    // Retorna a página de vendas trazendo o operador (Usuario do Caixa) para evitar N+1
    @Query(value = "SELECT v FROM Venda v JOIN FETCH v.caixa c JOIN FETCH c.operadorAbertura " +
            "WHERE (:status IS NULL OR v.status = :status)",
            countQuery = "SELECT count(v) FROM Venda v WHERE (:status IS NULL OR v.status = :status)")
    Page<Venda> buscarRelatorioVendas(@Param("status") StatusVenda status, Pageable pageable);

    // Busca os detalhes profundos de uma única venda (Recibo)
    @Query("SELECT v FROM Venda v " +
            "JOIN FETCH v.caixa c JOIN FETCH c.operadorAbertura " +
            "LEFT JOIN FETCH v.itens i LEFT JOIN FETCH i.produto " +
            "WHERE v.id = :id")
    Optional<Venda> buscarReciboComDetalhes(@Param("id") Long id);

    @Query("SELECT DISTINCT v FROM Venda v " +
            "LEFT JOIN FETCH v.maquininha " +
            "LEFT JOIN FETCH v.itens i " +
            "LEFT JOIN FETCH i.produto " +
            "WHERE v.dataVenda >= :inicio AND v.dataVenda <= :fim")
    List<Venda> buscarVendasParaDre(
            @Param("inicio") java.time.OffsetDateTime inicio,
            @Param("fim") java.time.OffsetDateTime fim
    );
}