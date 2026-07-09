package vexon.sellionpdv.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import vexon.sellionpdv.auth.dto.LoginRequestDTO;
import vexon.sellionpdv.common.exception.BusinessException;
import vexon.sellionpdv.config.GlobalExceptionHandler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static vexon.sellionpdv.auth.AuthTestFixtures.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController")
class AuthControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private AuthService authService;
    @InjectMocks private AuthController controller;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setApplicationContext(new StaticApplicationContext());
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    // ─── caminho feliz ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/auth/login — caminho feliz")
    class LoginSucesso {

        @Test
        @DisplayName("AC1 — Deve retornar 200 com token e dados do usuário quando credenciais são válidas")
        void deve_Retornar200ComLoginResponseDTO_quando_CredenciaisValidas() throws Exception {
            when(authService.realizarLogin(any(LoginRequestDTO.class)))
                    .thenReturn(umLoginResponseDTO());

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(umLoginRequestDTO())))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.accessToken").value(TOKEN_PADRAO))
                    .andExpect(jsonPath("$.refreshToken").value(REFRESH_TOKEN_PADRAO))
                    .andExpect(jsonPath("$.usuario.email").value("operador@test.com"));
        }
    }

    // ─── falhas de negócio ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/auth/login — falhas de negócio")
    class LoginFalhasNegocio {

        @Test
        @DisplayName("AC2 — Deve retornar 422 quando AuthService lança BusinessException (credenciais inválidas)")
        void deve_Retornar422_quando_AuthServiceLancaBusinessException() throws Exception {
            when(authService.realizarLogin(any(LoginRequestDTO.class)))
                    .thenThrow(new BusinessException("E-mail ou senha inválidos"));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(umLoginRequestDTO())))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    // ─── falhas de validação ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/auth/login — falhas de validação Bean Validation")
    class LoginFalhasValidacao {

        @Test
        @DisplayName("AC3 — Deve retornar 400 quando o e-mail está em branco (@NotBlank)")
        void deve_Retornar400_quando_EmailEmBranco() throws Exception {
            String body = objectMapper.writeValueAsString(new LoginRequestDTO("", "senha123"));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("AC4 — Deve retornar 400 quando o e-mail não tem formato válido (@Email)")
        void deve_Retornar400_quando_EmailFormatoInvalido() throws Exception {
            String body = objectMapper.writeValueAsString(new LoginRequestDTO("nao-e-um-email", "senha123"));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("AC5 — Deve retornar 400 quando a senha está em branco (@NotBlank)")
        void deve_Retornar400_quando_SenhaEmBranca() throws Exception {
            String body = objectMapper.writeValueAsString(new LoginRequestDTO("operador@test.com", ""));

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("AC6 — Deve retornar 400 quando o corpo da requisição está ausente")
        void deve_Retornar400_quando_CorpoAusente() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("AC7 — Deve retornar 400 quando o corpo da requisição é JSON malformado")
        void deve_Retornar400_quando_JsonMalformado() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ invalido }"))
                    .andExpect(status().isBadRequest());
        }
    }
}
