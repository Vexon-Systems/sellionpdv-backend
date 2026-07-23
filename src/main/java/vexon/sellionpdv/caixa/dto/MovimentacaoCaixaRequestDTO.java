package vexon.sellionpdv.caixa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Digits;
import vexon.sellionpdv.caixa.TipoMovimentacaoCaixa;

import java.math.BigDecimal;

public record MovimentacaoCaixaRequestDTO(

        @NotNull
        TipoMovimentacaoCaixa tipo,

        @NotNull
        @Positive
        @Digits(integer = 8, fraction = 2, message = "O valor deve possuir no máximo duas casas decimais")
        BigDecimal valor,

        @NotBlank
        String motivo

) {
}
