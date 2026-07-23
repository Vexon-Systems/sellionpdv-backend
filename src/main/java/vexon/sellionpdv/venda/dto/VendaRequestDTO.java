package vexon.sellionpdv.venda.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import vexon.sellionpdv.maquininha.BandeiraCartao;
import vexon.sellionpdv.venda.FormaPagamento;

import java.math.BigDecimal;
import java.util.List;

public record VendaRequestDTO(
        @Valid
        @NotEmpty(message = "A venda deve conter pelo menos um item")
        List<ItemVendaRequestDTO> itens,

        @NotNull(message = "A forma de pagamento é obrigatória")
        FormaPagamento formaPagamento,

        Long maquininhaId,

        BandeiraCartao bandeiraCartao,

        @PositiveOrZero(message = "O desconto não pode ser negativo")
        @Digits(integer = 8, fraction = 2, message = "O desconto deve possuir no máximo duas casas decimais")
        BigDecimal descontoAplicado,

        @Size(max = 500, message = "O motivo do desconto deve ter no máximo 500 caracteres")
        String motivoDesconto
) {
    public VendaRequestDTO(
            List<ItemVendaRequestDTO> itens,
            FormaPagamento formaPagamento,
            Long maquininhaId,
            BandeiraCartao bandeiraCartao,
            BigDecimal descontoAplicado
    ) {
        this(itens, formaPagamento, maquininhaId, bandeiraCartao, descontoAplicado, null);
    }
}
