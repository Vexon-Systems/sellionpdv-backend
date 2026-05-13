package vexon.sellionpdv.caixa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import vexon.sellionpdv.caixa.TipoMovimentacaoCaixa;

import java.math.BigDecimal;

public record MovimentacaoCaixaRequestDTO(

        @NotNull
        TipoMovimentacaoCaixa tipo,

        @NotNull
        @Positive
        BigDecimal valor,

        @NotBlank
        String motivo

) {
}