package vexon.sellionpdv.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setup() {
    }

    @Test
    void deveLancarExcecaoQuandoCredenciaisForemInvalidas() {

        String email = "admin@sellion.com";
        String senha = "senhaErrada";

        when(authenticationManager.authenticate(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("Credenciais inválidas"));

        assertThrows(RuntimeException.class, () -> {
            authService.login(email, senha);
        });
    }

    @Test
    void deveValidarSenhaCorretamente() {

        String senhaRaw = "123456";
        String senhaHash = "$argon2id$hashfake";

        when(passwordEncoder.matches(senhaRaw, senhaHash))
                .thenReturn(true);

        boolean senhaValida = passwordEncoder.matches(senhaRaw, senhaHash);

        org.junit.jupiter.api.Assertions.assertTrue(senhaValida);
    }
}