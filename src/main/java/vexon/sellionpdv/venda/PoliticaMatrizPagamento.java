package vexon.sellionpdv.venda;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vexon.sellionpdv.common.exception.CodedHttpException;
import vexon.sellionpdv.maquininha.BandeiraCartao;

@Component
public class PoliticaMatrizPagamento {

    public void validar(
            FormaPagamento formaPagamento,
            Long maquininhaId,
            BandeiraCartao bandeiraCartao
    ) {
        if (formaPagamento == null) {
            throw new CodedHttpException(
                    HttpStatus.BAD_REQUEST,
                    "VALIDACAO_INVALIDA",
                    "A forma de pagamento é obrigatória.");
        }

        boolean possuiMaquininha = maquininhaId != null;
        boolean possuiBandeira = bandeiraCartao != null;
        boolean combinacaoValida = switch (formaPagamento) {
            case DINHEIRO, PIX -> !possuiMaquininha && !possuiBandeira;
            case CREDITO, DEBITO -> possuiMaquininha && possuiBandeira;
        };

        if (!combinacaoValida) {
            throw new CodedHttpException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "MATRIZ_PAGAMENTO_INVALIDA",
                    "A combinação entre forma de pagamento, maquininha e bandeira é inválida.");
        }
    }
}
