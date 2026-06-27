package vexon.sellionpdv.auth;

import vexon.sellionpdv.auth.dto.LoginRequestDTO;
import vexon.sellionpdv.auth.dto.LoginResponseDTO;
import vexon.sellionpdv.auth.dto.UsuarioAuthDTO;
import vexon.sellionpdv.tenant.Tenant;
import vexon.sellionpdv.usuario.Usuario;

public final class AuthTestFixtures {

    // SharedTestFixtures não existe neste projeto — arquitetura package-by-feature mantém
    // fixtures por pacote de domínio, então cada pacote tem suas próprias constantes.
    public static final String TOKEN_PADRAO = "token.jwt.aqui";

    private AuthTestFixtures() {}

    public static Tenant umTenant() {
        return Tenant.builder().id(1L).nomeFantasia("Franquia Teste").build();
    }

    public static Usuario umUsuario(Tenant tenant) {
        return Usuario.builder()
                .id(1L)
                .nome("Operador")
                .email("operador@test.com")
                .senhaHash("hash-qualquer-nao-verificado-aqui")
                .role("ROLE_ADMIN")
                .tenant(tenant)
                .ativo(true)
                .build();
    }

    public static Usuario umUsuarioInativo(Tenant tenant) {
        return Usuario.builder()
                .id(2L)
                .nome("Operador Inativo")
                .email("operador@test.com")
                .senhaHash("hash-qualquer-nao-verificado-aqui")
                .role("ROLE_ADMIN")
                .tenant(tenant)
                .ativo(false)
                .build();
    }

    public static LoginRequestDTO umLoginRequestDTO() {
        return new LoginRequestDTO("operador@test.com", "senha123");
    }

    public static LoginResponseDTO umLoginResponseDTO() {
        return new LoginResponseDTO(
                TOKEN_PADRAO,
                new UsuarioAuthDTO(1L, "Operador", "operador@test.com", "ROLE_ADMIN")
        );
    }
}
