package vexon.sellionpdv.relatorio.dto;

import java.math.BigDecimal;

public record ReciboModificadorDTO(
        String nomeOpcao,
        BigDecimal valorAdicional
) {}
