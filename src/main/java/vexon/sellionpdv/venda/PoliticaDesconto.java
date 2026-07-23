package vexon.sellionpdv.venda;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vexon.sellionpdv.common.exception.CodedHttpException;
import vexon.sellionpdv.usuario.Usuario;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class PoliticaDesconto {

    static final String ROLE_ADMIN = "ROLE_ADMIN";
    static final String ROLE_OPERADOR = "ROLE_OPERADOR";
    static final int LIMITE_MOTIVO = 500;

    private static final BigDecimal PERCENTUAL_OPERADOR = new BigDecimal("0.10");
    private static final BigDecimal PERCENTUAL_ADMIN = new BigDecimal("0.30");

    public DescontoValidado validar(
            BigDecimal descontoInformado,
            String motivoInformado,
            BigDecimal subtotal,
            Usuario usuarioPersistido
    ) {
        BigDecimal desconto = descontoInformado == null ? new BigDecimal("0.00") : descontoInformado;
        validarFormato(desconto, motivoInformado);

        String motivo = normalizarMotivo(motivoInformado);
        if (desconto.signum() == 0) {
            if (motivo != null) {
                throw erro(HttpStatus.UNPROCESSABLE_ENTITY, "MOTIVO_DESCONTO_SEM_DESCONTO",
                        "O motivo de desconto não deve ser informado quando não há desconto.");
            }
            return new DescontoValidado(desconto.setScale(2), null);
        }

        BigDecimal percentual = percentualPermitido(usuarioPersistido);
        if (motivo == null) {
            throw erro(HttpStatus.UNPROCESSABLE_ENTITY, "MOTIVO_DESCONTO_OBRIGATORIO",
                    "O motivo do desconto é obrigatório.");
        }

        if (subtotal == null || desconto.compareTo(subtotal) >= 0) {
            throw erro(HttpStatus.UNPROCESSABLE_ENTITY, "DESCONTO_INTEGRAL_NAO_PERMITIDO",
                    "O desconto deve ser menor que o subtotal da venda.");
        }

        BigDecimal limite = subtotal.multiply(percentual).setScale(2, RoundingMode.DOWN);
        if (desconto.compareTo(limite) > 0) {
            throw erro(HttpStatus.UNPROCESSABLE_ENTITY, "DESCONTO_ACIMA_DA_ALCADA",
                    "O desconto informado excede a alçada do usuário.");
        }

        return new DescontoValidado(desconto.setScale(2), motivo);
    }

    private void validarFormato(BigDecimal desconto, String motivo) {
        if (desconto.signum() < 0 || desconto.scale() > 2) {
            throw erro(HttpStatus.BAD_REQUEST, "VALIDACAO_INVALIDA",
                    "O desconto deve ser não negativo e possuir no máximo duas casas decimais.");
        }
        if (motivo != null && motivo.length() > LIMITE_MOTIVO) {
            throw erro(HttpStatus.BAD_REQUEST, "VALIDACAO_INVALIDA",
                    "O motivo do desconto deve ter no máximo 500 caracteres.");
        }
    }

    private BigDecimal percentualPermitido(Usuario usuario) {
        String role = usuario != null ? usuario.getRole() : null;
        return switch (role) {
            case ROLE_OPERADOR -> PERCENTUAL_OPERADOR;
            case ROLE_ADMIN -> PERCENTUAL_ADMIN;
            default -> throw erro(HttpStatus.FORBIDDEN, "DESCONTO_NAO_AUTORIZADO",
                    "O usuário não possui permissão para conceder desconto.");
        };
    }

    private String normalizarMotivo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            return null;
        }
        return motivo.trim();
    }

    private CodedHttpException erro(HttpStatus status, String code, String message) {
        return new CodedHttpException(status, code, message);
    }

    public record DescontoValidado(BigDecimal valor, String motivo) {
    }
}
