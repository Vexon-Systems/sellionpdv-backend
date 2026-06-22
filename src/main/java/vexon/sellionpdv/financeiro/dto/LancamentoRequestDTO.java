package vexon.sellionpdv.financeiro.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import vexon.sellionpdv.financeiro.CategoriaLancamento;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LancamentoRequestDTO(
        @NotBlank(message = "A descrição é obrigatória")
        String descricao,

        @NotNull(message = "O valor é obrigatório")
        @Positive(message = "O valor deve ser positivo")
        BigDecimal valor,

        @NotNull(message = "A categoria é obrigatória")
        CategoriaLancamento categoria,

        @NotNull(message = "A data de referência é obrigatória")
        LocalDate dataReferencia
) {}
