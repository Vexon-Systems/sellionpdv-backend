package vexon.sellionpdv.relatorio.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// DTO para a Listagem Paginada (GET /api/relatorios/vendas)
public record ReciboVendaResponseDTO(
        Long vendaId,
        Instant dataVenda,
        Long caixaId,
        String nomeOperador,
        BigDecimal valorTotal,
        String formaPagamento,
        String status,
        String justificativaCancelamento,
        Instant dataCancelamento,
        List<ReciboItemDTO> itens
) {}
