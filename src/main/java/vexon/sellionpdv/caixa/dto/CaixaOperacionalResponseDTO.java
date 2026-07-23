package vexon.sellionpdv.caixa.dto;

import vexon.sellionpdv.caixa.StatusCaixa;

import java.time.OffsetDateTime;
import java.util.List;

public record CaixaOperacionalResponseDTO(
        boolean caixaAberto,
        boolean visaoAdministrativa,
        Long id,
        StatusCaixa status,
        OffsetDateTime dataAbertura,
        Long operadorAberturaId,
        String operadorAberturaNome,
        List<EventoCaixaOperacionalResponseDTO> eventos
) {
    public static CaixaOperacionalResponseDTO semCaixaAberto(boolean visaoAdministrativa) {
        return new CaixaOperacionalResponseDTO(
                false,
                visaoAdministrativa,
                null,
                null,
                null,
                null,
                null,
                List.of());
    }
}
