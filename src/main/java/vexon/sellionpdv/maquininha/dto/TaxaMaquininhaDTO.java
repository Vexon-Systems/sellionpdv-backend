package vexon.sellionpdv.maquininha.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import vexon.sellionpdv.maquininha.BandeiraCartao;
import vexon.sellionpdv.maquininha.TipoTransacaoCartao;

import java.math.BigDecimal;

public record TaxaMaquininhaDTO(
        @NotNull BandeiraCartao bandeira,
        @NotNull TipoTransacaoCartao tipo,
        @NotNull @PositiveOrZero BigDecimal taxa
) {}
