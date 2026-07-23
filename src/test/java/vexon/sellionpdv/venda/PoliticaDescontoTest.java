package vexon.sellionpdv.venda;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import vexon.sellionpdv.common.exception.CodedHttpException;
import vexon.sellionpdv.usuario.Usuario;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("SEL-SEC-003 — política central de descontos")
class PoliticaDescontoTest {

    private final PoliticaDesconto politica = new PoliticaDesconto();

    @Test
    @DisplayName("limites globais exatos de 10% e 30% são inclusivos")
    void limitesGlobaisExatosSaoInclusivos() {
        assertEquals(new BigDecimal("10.00"), politica.validar(
                new BigDecimal("10.00"), "Operador", new BigDecimal("100.00"),
                usuario("ROLE_OPERADOR")).valor());
        assertEquals(new BigDecimal("30.00"), politica.validar(
                new BigDecimal("30.00"), "Administrador", new BigDecimal("100.00"),
                usuario("ROLE_ADMIN")).valor());
    }

    @Test
    @DisplayName("valores um centavo abaixo dos limites globais são aceitos")
    void valoresUmCentavoAbaixoDosLimitesSaoAceitos() {
        assertEquals(new BigDecimal("9.99"), politica.validar(
                new BigDecimal("9.99"), "Operador", new BigDecimal("100.00"),
                usuario("ROLE_OPERADOR")).valor());
        assertEquals(new BigDecimal("29.99"), politica.validar(
                new BigDecimal("29.99"), "Administrador", new BigDecimal("100.00"),
                usuario("ROLE_ADMIN")).valor());
    }

    @Test
    @DisplayName("operador aceita teto de 10% arredondado para baixo")
    void operadorAceitaTeto() {
        var resultado = politica.validar(
                new BigDecimal("9.99"), "  Fidelização  ", new BigDecimal("99.99"), usuario("ROLE_OPERADOR"));

        assertEquals(new BigDecimal("9.99"), resultado.valor());
        assertEquals("Fidelização", resultado.motivo());
    }

    @Test
    @DisplayName("operador rejeita um centavo acima do teto")
    void operadorRejeitaAcimaDoTeto() {
        assertErro("DESCONTO_ACIMA_DA_ALCADA", HttpStatus.UNPROCESSABLE_ENTITY, () ->
                politica.validar(new BigDecimal("10.00"), "Tentativa", new BigDecimal("99.99"),
                        usuario("ROLE_OPERADOR")));
    }

    @Test
    @DisplayName("administrador aceita teto de 30% arredondado para baixo")
    void administradorAceitaTeto() {
        var resultado = politica.validar(
                new BigDecimal("29.99"), "Autorizado", new BigDecimal("99.99"), usuario("ROLE_ADMIN"));

        assertEquals(new BigDecimal("29.99"), resultado.valor());
    }

    @Test
    @DisplayName("administrador rejeita um centavo acima do teto")
    void administradorRejeitaAcimaDoTeto() {
        assertErro("DESCONTO_ACIMA_DA_ALCADA", HttpStatus.UNPROCESSABLE_ENTITY, () ->
                politica.validar(new BigDecimal("30.00"), "Tentativa", new BigDecimal("99.99"),
                        usuario("ROLE_ADMIN")));
    }

    @Test
    @DisplayName("papel persistido desconhecido não concede desconto")
    void papelDesconhecidoNaoConcedeDesconto() {
        assertErro("DESCONTO_NAO_AUTORIZADO", HttpStatus.FORBIDDEN, () ->
                politica.validar(new BigDecimal("1.00"), "Tentativa", new BigDecimal("100.00"),
                        usuario("ROLE_AUDITOR")));
    }

    @Test
    @DisplayName("desconto positivo exige motivo")
    void descontoPositivoExigeMotivo() {
        assertErro("MOTIVO_DESCONTO_OBRIGATORIO", HttpStatus.UNPROCESSABLE_ENTITY, () ->
                politica.validar(new BigDecimal("1.00"), "   ", new BigDecimal("100.00"),
                        usuario("ROLE_OPERADOR")));
    }

    @Test
    @DisplayName("motivo sem desconto é rejeitado")
    void motivoSemDescontoEhRejeitado() {
        assertErro("MOTIVO_DESCONTO_SEM_DESCONTO", HttpStatus.UNPROCESSABLE_ENTITY, () ->
                politica.validar(BigDecimal.ZERO, "Motivo ambíguo", new BigDecimal("100.00"),
                        usuario("ROLE_OPERADOR")));
    }

    @Test
    @DisplayName("espaços sem desconto são normalizados para ausência")
    void espacosSemDescontoSaoNormalizados() {
        var resultado = politica.validar(null, "   ", new BigDecimal("100.00"), usuario("ROLE_AUDITOR"));

        assertEquals(new BigDecimal("0.00"), resultado.valor());
        assertNull(resultado.motivo());
    }

    @Test
    @DisplayName("desconto integral é sempre proibido")
    void descontoIntegralEhProibido() {
        assertErro("DESCONTO_INTEGRAL_NAO_PERMITIDO", HttpStatus.UNPROCESSABLE_ENTITY, () ->
                politica.validar(new BigDecimal("100.00"), "Gratuidade", new BigDecimal("100.00"),
                        usuario("ROLE_ADMIN")));
    }

    @Test
    @DisplayName("fração extra, mesmo zero, é rejeitada")
    void escalaMaiorQueDuasCasasEhRejeitada() {
        assertErro("VALIDACAO_INVALIDA", HttpStatus.BAD_REQUEST, () ->
                politica.validar(new BigDecimal("10.000"), "Escala inválida", new BigDecimal("100.00"),
                        usuario("ROLE_ADMIN")));
    }

    @Test
    @DisplayName("desconto negativo é rejeitado")
    void descontoNegativoEhRejeitado() {
        assertErro("VALIDACAO_INVALIDA", HttpStatus.BAD_REQUEST, () ->
                politica.validar(new BigDecimal("-0.01"), "Inválido", new BigDecimal("100.00"),
                        usuario("ROLE_ADMIN")));
    }

    @Test
    @DisplayName("motivo com exatamente 500 caracteres é aceito")
    void motivoNoLimiteEhAceito() {
        var resultado = politica.validar(
                new BigDecimal("1.00"), "x".repeat(500), new BigDecimal("100.00"),
                usuario("ROLE_OPERADOR"));

        assertEquals(500, resultado.motivo().length());
    }

    @Test
    @DisplayName("motivo acima de 500 caracteres é rejeitado")
    void motivoAcimaDoLimiteEhRejeitado() {
        assertErro("VALIDACAO_INVALIDA", HttpStatus.BAD_REQUEST, () ->
                politica.validar(new BigDecimal("1.00"), "x".repeat(501), new BigDecimal("100.00"),
                        usuario("ROLE_OPERADOR")));
    }

    private Usuario usuario(String role) {
        return Usuario.builder().id(1L).email("teste@invalid").nome("Teste")
                .senhaHash("hash").role(role).ativo(true).build();
    }

    private void assertErro(String code, HttpStatus status, Runnable acao) {
        CodedHttpException ex = assertThrows(CodedHttpException.class, acao::run);
        assertEquals(code, ex.getCode());
        assertEquals(status, ex.getStatus());
    }
}
