package vexon.sellionpdv.venda;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import vexon.sellionpdv.common.exception.CodedHttpException;
import vexon.sellionpdv.maquininha.BandeiraCartao;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("SEL-SEC-004 — política central da matriz de pagamento")
class PoliticaMatrizPagamentoTest {

    private final PoliticaMatrizPagamento politica = new PoliticaMatrizPagamento();

    @ParameterizedTest(name = "{0} | maquininha={1} | bandeira={2} | válida={3}")
    @MethodSource("matrizCompleta")
    void validaAsDezesseisCombinacoes(
            FormaPagamento forma,
            boolean possuiMaquininha,
            boolean possuiBandeira,
            boolean valida
    ) {
        Long maquininhaId = possuiMaquininha ? 1L : null;
        BandeiraCartao bandeira = possuiBandeira ? BandeiraCartao.VISA : null;

        if (valida) {
            assertDoesNotThrow(() -> politica.validar(forma, maquininhaId, bandeira));
            return;
        }

        CodedHttpException exception = assertThrows(CodedHttpException.class,
                () -> politica.validar(forma, maquininhaId, bandeira));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.getStatus());
        assertEquals("MATRIZ_PAGAMENTO_INVALIDA", exception.getCode());
    }

    @Test
    @DisplayName("forma ausente retorna erro de formato estável")
    void formaAusenteEhValidacaoInvalida() {
        CodedHttpException exception = assertThrows(CodedHttpException.class,
                () -> politica.validar(null, null, null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("VALIDACAO_INVALIDA", exception.getCode());
    }

    private static Stream<Arguments> matrizCompleta() {
        return Stream.of(FormaPagamento.values())
                .flatMap(forma -> Stream.of(false, true)
                        .flatMap(possuiMaquininha -> Stream.of(false, true)
                                .map(possuiBandeira -> Arguments.of(
                                        forma,
                                        possuiMaquininha,
                                        possuiBandeira,
                                        combinacaoValida(forma, possuiMaquininha, possuiBandeira)))));
    }

    private static boolean combinacaoValida(
            FormaPagamento forma,
            boolean possuiMaquininha,
            boolean possuiBandeira
    ) {
        return switch (forma) {
            case DINHEIRO, PIX -> !possuiMaquininha && !possuiBandeira;
            case CREDITO, DEBITO -> possuiMaquininha && possuiBandeira;
        };
    }
}
