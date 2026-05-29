package vexon.sellionpdv.relatorio.dto;

import java.math.BigDecimal;
import java.util.List;

// DTOs para o Recibo Detalhado (GET /api/relatorios/vendas/{id})
public record ReciboItemDTO(
        Long produtoId,
        String nomeProduto,
        Integer quantidade,
        BigDecimal valorUnitario,
        BigDecimal subtotalItem,
        List<ReciboModificadorDTO> modificadores
) {}
