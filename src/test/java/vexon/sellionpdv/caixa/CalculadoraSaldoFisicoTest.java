package vexon.sellionpdv.caixa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("SEL-SEC-007 — calculadora pura do saldo físico")
class CalculadoraSaldoFisicoTest {

    private final CalculadoraSaldoFisico calculadora = new CalculadoraSaldoFisico();

    @Test
    @DisplayName("aplica fórmula e preserva centavos sem arredondamento")
    void calculaFormulaAprovada() {
        CalculadoraSaldoFisico.Resultado resultado = calculadora.calcular(
                new BigDecimal("100.00"),
                new BigDecimal("100.00"),
                new BigDecimal("25.50"),
                new BigDecimal("10.25"),
                new BigDecimal("210.00"));

        assertEquals(new BigDecimal("215.25"), resultado.saldoEsperado());
        assertEquals(new BigDecimal("-5.25"), resultado.furoCaixa());
    }

    @Test
    @DisplayName("preserva saldo esperado negativo sem clamp")
    void preservaSaldoEsperadoNegativo() {
        CalculadoraSaldoFisico.Resultado resultado = calculadora.calcular(
                new BigDecimal("50.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("100.00"),
                BigDecimal.ZERO);

        assertEquals(new BigDecimal("-50.00"), resultado.saldoEsperado());
        assertEquals(new BigDecimal("50.00"), resultado.furoCaixa());
    }

    @Test
    @DisplayName("caixa vazio mantém saldo inicial e diferença zero")
    void caixaVazioMantemSaldoInicial() {
        CalculadoraSaldoFisico.Resultado resultado = calculadora.calcular(
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("100.00"));

        assertEquals(new BigDecimal("100.00"), resultado.saldoEsperado());
        assertEquals(new BigDecimal("0.00"), resultado.furoCaixa());
    }
}
