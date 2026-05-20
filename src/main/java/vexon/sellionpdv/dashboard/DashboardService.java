package vexon.sellionpdv.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vexon.sellionpdv.caixa.CaixaRepository;
import vexon.sellionpdv.caixa.MovimentacaoCaixaRepository;
import vexon.sellionpdv.dashboard.dto.*;
import vexon.sellionpdv.venda.FormaPagamento;
import vexon.sellionpdv.venda.VendaRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;


@Service
@RequiredArgsConstructor
public class DashboardService {

    private final VendaRepository vendaRepository;
    private final CaixaRepository caixaRepository;
    private final MovimentacaoCaixaRepository movimentacaoCaixaRepository;

    private static final ZoneId ZONE_LOCAL = ZoneId.of("America/Sao_Paulo");

    private OffsetDateTime getInicioDoDia(LocalDate data) {
        return data.atStartOfDay(ZONE_LOCAL).toOffsetDateTime();
    }

    private OffsetDateTime getFimDoDia(LocalDate data) {
        return data.atTime(LocalTime.MAX).atZone(ZONE_LOCAL).toOffsetDateTime();
    }

    public KpiResponseDTO obterKpis(LocalDate dataInicial, LocalDate dataFinal) {
        Object[][] result = vendaRepository.obterKpisDeVendas(getInicioDoDia(dataInicial), getFimDoDia(dataFinal));

        if (result == null || result.length == 0 || result[0][0] == null) {
            return new KpiResponseDTO(BigDecimal.ZERO, 0L, BigDecimal.ZERO);
        }

        BigDecimal faturamentoTotal = (BigDecimal) result[0][0];
        Long quantidadeVendas = (Long) result[0][1];

        BigDecimal ticketMedio = BigDecimal.ZERO;
        if (quantidadeVendas > 0) {
            ticketMedio = faturamentoTotal.divide(BigDecimal.valueOf(quantidadeVendas), 2, RoundingMode.HALF_UP);
        }

        return new KpiResponseDTO(faturamentoTotal, quantidadeVendas, ticketMedio);
    }

    public List<PagamentoDashboardDTO> obterPagamentos(LocalDate dataInicial, LocalDate dataFinal) {
        List<Object[]> resultados = vendaRepository.agruparFaturamentoPorPagamento(getInicioDoDia(dataInicial), getFimDoDia(dataFinal));

        // Total geral para o cálculo da porcentagem
        KpiResponseDTO kpis = obterKpis(dataInicial, dataFinal);
        BigDecimal totalGeral = kpis.faturamentoTotal();

        List<PagamentoDashboardDTO> lista = new ArrayList<>();

        for (Object[] linha : resultados) {
            FormaPagamento forma = (FormaPagamento) linha[0];
            BigDecimal valorTotal = (BigDecimal) linha[1];
            Long qtd = (Long) linha[2];

            BigDecimal percentual = BigDecimal.ZERO;
            if (totalGeral.compareTo(BigDecimal.ZERO) > 0) {
                percentual = valorTotal.divide(totalGeral, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            }

            lista.add(new PagamentoDashboardDTO(forma, valorTotal, qtd, percentual));
        }

        return lista;
    }

    public List<ProdutoTopResponseDTO> obterProdutosTop(LocalDate dataInicial, LocalDate dataFinal) {
        return vendaRepository.obterProdutosTop(getInicioDoDia(dataInicial), getFimDoDia(dataFinal))
                .stream()
                .map(linha -> new ProdutoTopResponseDTO(
                        (Long) linha[0],
                        (String) linha[1],
                        (Long) linha[2],
                        (BigDecimal) linha[3]
                )).toList();
    }

    public List<CategoriaDashboardResponseDTO> obterCategoriasDashboard(LocalDate dataInicial, LocalDate dataFinal) {
        List<Object[]> resultados = vendaRepository.obterFaturamentoPorCategoria(getInicioDoDia(dataInicial), getFimDoDia(dataFinal));
        BigDecimal totalGeral = obterKpis(dataInicial, dataFinal).faturamentoTotal();

        return resultados.stream().map(linha -> {
            BigDecimal valorGerado = (BigDecimal) linha[3];
            BigDecimal percentual = BigDecimal.ZERO;
            if (totalGeral.compareTo(BigDecimal.ZERO) > 0) {
                percentual = valorGerado.divide(totalGeral, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
            }
            return new CategoriaDashboardResponseDTO(
                    (Long) linha[0],
                    (String) linha[1],
                    (Long) linha[2],
                    valorGerado,
                    percentual
            );
        }).toList();
    }

    public List<SerieTemporalResponseDTO> obterSerieTemporal(LocalDate dataInicial, LocalDate dataFinal) {
        OffsetDateTime inicio = getInicioDoDia(dataInicial);
        OffsetDateTime fim = getFimDoDia(dataFinal);

        List<Object[]> resultados;
        // Se a busca for restrita ao mesmo dia, agrupa por hora, caso contrário, por dia.
        if (dataInicial.isEqual(dataFinal)) {
            resultados = vendaRepository.obterSerieTemporalPorHora(inicio, fim);
        } else {
            resultados = vendaRepository.obterSerieTemporalPorDia(inicio, fim);
        }

        return resultados.stream()
                .map(linha -> new SerieTemporalResponseDTO((String) linha[0], (BigDecimal) linha[1]))
                .toList();
    }

    public CaixaDashboardResponseDTO obterDadosCaixa(LocalDate dataInicial, LocalDate dataFinal) {
        OffsetDateTime inicio = getInicioDoDia(dataInicial);
        OffsetDateTime fim = getFimDoDia(dataFinal);

        Object[][] dadosCaixa = caixaRepository.obterDadosResumidosCaixa(inicio, fim);
        List<Object[]> movimentacoes = movimentacaoCaixaRepository.obterTotalMovimentacoesPorPeriodo(inicio, fim);

        if (dadosCaixa == null || dadosCaixa.length == 0 || dadosCaixa[0][0] == null) {
            return new CaixaDashboardResponseDTO(0L, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        Long turnosAbertos = (Long) dadosCaixa[0][0];

        Double saldoMedioDouble = (Double) dadosCaixa[0][1];
        BigDecimal saldoInicialMedio = saldoMedioDouble != null ?
                BigDecimal.valueOf(saldoMedioDouble).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        BigDecimal diferencaCaixaTotal = (BigDecimal) dadosCaixa[0][2];

        BigDecimal totalSangrias = BigDecimal.ZERO;
        BigDecimal totalReforcos = BigDecimal.ZERO;

        for (Object[] mov : movimentacoes) {
            var tipo = mov[0].toString();
            BigDecimal valor = (BigDecimal) mov[1];
            if ("SANGRIA".equals(tipo)) {
                totalSangrias = valor;
            } else if ("REFORCO".equals(tipo)) {
                totalReforcos = valor;
            }
        }

        return new CaixaDashboardResponseDTO(
                turnosAbertos,
                totalSangrias,
                totalReforcos,
                saldoInicialMedio,
                diferencaCaixaTotal != null ? diferencaCaixaTotal : BigDecimal.ZERO
        );
    }
}