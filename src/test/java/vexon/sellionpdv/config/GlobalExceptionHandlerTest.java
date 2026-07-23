package vexon.sellionpdv.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import vexon.sellionpdv.common.exception.CodedHttpException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Testes unitários diretos dos handlers de exceção mais específicos — sem MockMvc nem
 * contexto Spring, já que GlobalExceptionHandler não tem dependências injetadas.
 * Preferido a um teste via MockMvc para MaxUploadSizeExceededException: essa exceção é
 * lançada pelo container de servlet ao processar o multipart, e MockMvc não reproduz
 * de forma confiável o mesmo enforcement de spring.servlet.multipart.max-file-size que
 * um servidor real — testar o handler diretamente evita esse ruído.
 */
@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("SEL-SEC-003 — deve preservar status e código interno estável")
    void deve_PreservarStatusECodigoInterno() {
        var ex = new CodedHttpException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "DESCONTO_ACIMA_DA_ALCADA",
                "Desconto rejeitado");

        ResponseEntity<ProblemDetail> response = handler.handleCodedHttp(ex);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals("DESCONTO_ACIMA_DA_ALCADA", response.getBody().getProperties().get("code"));
    }

    @Nested
    @DisplayName("handleUploadTooLarge (SAST-21)")
    class HandleUploadTooLarge {

        @Test
        @DisplayName("Deve retornar 413 com mensagem amigável")
        void deve_Retornar413_comMensagemAmigavel() {
            var ex = new MaxUploadSizeExceededException(5_242_880L);

            ResponseEntity<ProblemDetail> response = handler.handleUploadTooLarge(ex);

            assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
            assertEquals("Arquivo excede o tamanho máximo permitido de 5 MB.",
                    response.getBody().getDetail());
        }
    }

    @Nested
    @DisplayName("handleAccessDenied")
    class HandleAccessDenied {

        @Test
        @DisplayName("Deve retornar 403 (regressão do bug que mascarava @PreAuthorize como 500)")
        void deve_Retornar403() {
            ResponseEntity<ProblemDetail> response =
                    handler.handleAccessDenied(new AccessDeniedException("negado"));

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        }
    }
}
