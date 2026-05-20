package vexon.sellionpdv.venda;

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
    @Query("SELECT FUNCTION('to_char', v.dataVenda, 'HH24:00') as hora, SUM(v.totalFinal) " +
            "FROM Venda v " +
            "WHERE v.status = 'CONCLUIDA' " +
            "AND v.dataVenda >= :inicio AND v.dataVenda <= :fim " +
            "GROUP BY FUNCTION('to_char', v.dataVenda, 'HH24:00') " +
            "ORDER BY hora")
    List<Object[]> obterSerieTemporalPorHora(@Param("inicio") OffsetDateTime inicio, @Param("fim") OffsetDateTime fim);

    // Série Temporal - Agrupamento por Dia
    @Query("SELECT FUNCTION('to_char', v.dataVenda, 'DD/MM') as dia, SUM(v.totalFinal) " +
            "FROM Venda v " +
            "WHERE v.status = 'CONCLUIDA' " +
            "AND v.dataVenda >= :inicio AND v.dataVenda <= :fim " +
            "GROUP BY FUNCTION('to_char', v.dataVenda, 'DD/MM') " +
            "ORDER BY dia")
    List<Object[]> obterSerieTemporalPorDia(@Param("inicio") OffsetDateTime inicio, @Param("fim") OffsetDateTime fim);
}