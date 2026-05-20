package vexon.sellionpdv.dashboard.dto;

import vexon.sellionpdv.venda.FormaPagamento;
import java.math.BigDecimal;

public record PagamentoDashboardDTO(
        FormaPagamento formaPagamento,
        BigDecimal valorTotal,
        Long quantidadeTransacoes,
        BigDecimal percentualFaturamento
) {}