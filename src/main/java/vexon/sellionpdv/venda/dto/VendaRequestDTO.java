package vexon.sellionpdv.venda.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import vexon.sellionpdv.maquininha.BandeiraCartao;
import vexon.sellionpdv.venda.FormaPagamento;

import java.math.BigDecimal;
import java.util.List;

public record VendaRequestDTO(
        @NotEmpty(message = "A venda deve conter pelo menos um item")
        List<ItemVendaRequestDTO> itens,

        @NotNull(message = "A forma de pagamento é obrigatória")
        FormaPagamento formaPagamento,

        Long maquininhaId,

        BandeiraCartao bandeiraCartao,

        BigDecimal descontoAplicado
) {}