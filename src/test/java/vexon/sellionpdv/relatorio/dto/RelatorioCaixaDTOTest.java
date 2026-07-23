package vexon.sellionpdv.relatorio.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("SEL-SEC-007 — saldo físico no relatório de caixas")
class RelatorioCaixaDTOTest {

    @Test
    @DisplayName("mantém total financeiro, mas calcula saldo físico somente com vendas em dinheiro")
    void deveSepararTotalFinanceiroDeSaldoFisico() {
        RelatorioCaixaDTO dto = new RelatorioCaixaDTO(
                1L,
                "FECHADO",
                "Operador",
                "Administrador",
                OffsetDateTime.parse("2026-07-23T10:00:00-03:00"),
                OffsetDateTime.parse("2026-07-23T18:00:00-03:00"),
                new BigDecimal("100.00"),
                new BigDecimal("650.00"),
                new BigDecimal("100.00"),
                new BigDecimal("10.25"),
                new BigDecimal("25.50"),
                new BigDecimal("210.00"));

        assertEquals(new BigDecimal("650.00"), dto.totalVendas());
        assertEquals(new BigDecimal("215.25"), dto.saldoFinalCalculado());
        assertEquals(new BigDecimal("-5.25"), dto.furoCaixa());
    }
}
