package vexon.sellionpdv.caixa;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CalculadoraSaldoFisico {

    public Resultado calcular(
            BigDecimal saldoInicial,
            BigDecimal totalVendasDinheiro,
            BigDecimal totalReforcos,
            BigDecimal totalSangrias,
            BigDecimal saldoFinalInformado
    ) {
        BigDecimal saldoEsperado = saldoInicial
                .add(totalVendasDinheiro)
                .add(totalReforcos)
                .subtract(totalSangrias);
        BigDecimal furoCaixa = saldoFinalInformado.subtract(saldoEsperado);

        return new Resultado(saldoEsperado, furoCaixa);
    }

    public record Resultado(BigDecimal saldoEsperado, BigDecimal furoCaixa) {
    }
}
