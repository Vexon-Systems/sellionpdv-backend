package vexon.sellionpdv.auth;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vexon.sellionpdv.auth.dto.LoginRequestDTO;
import vexon.sellionpdv.auth.dto.LoginResponseDTO;
import vexon.sellionpdv.auth.dto.LogoutRequestDTO;
import vexon.sellionpdv.auth.dto.RefreshRequestDTO;
import vexon.sellionpdv.auth.dto.UsuarioAuthDTO;
import vexon.sellionpdv.common.exception.BusinessException;
import vexon.sellionpdv.security.TokenService;
import vexon.sellionpdv.usuario.Usuario;
import vexon.sellionpdv.usuario.UsuarioRepository;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;

    // SAST-11: comparado quando o e-mail não existe, para igualar o tempo de resposta
    // ao caminho "e-mail existe, senha errada" — sem isso, pular a verificação Argon2
    // (lenta de propósito) cria um canal de tempo que permite enumerar e-mails.
    private String hashDummy;

    @PostConstruct
    void gerarHashDummy() {
        hashDummy = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public LoginResponseDTO realizarLogin(LoginRequestDTO request) {
        Optional<Usuario> usuarioOpt = usuarioRepository.findByEmailWithTenant(request.email());

        boolean senhaConfere = passwordEncoder.matches(
                request.senha(),
                usuarioOpt.map(Usuario::getSenhaHash).orElse(hashDummy));

        if (usuarioOpt.isEmpty() || !senhaConfere || !usuarioOpt.get().getAtivo()) {
            throw new BusinessException("E-mail ou senha inválidos");
        }

        return montarResposta(usuarioOpt.get());
    }

    @Transactional
    public LoginResponseDTO renovarToken(RefreshRequestDTO request) {
        RefreshToken tokenAntigo = refreshTokenService.validarERevogar(request.refreshToken());
        return montarResposta(tokenAntigo.getUsuario());
    }

    @Transactional
    public void logout(LogoutRequestDTO request) {
        refreshTokenService.revogarPorToken(request.refreshToken());
    }

    private LoginResponseDTO montarResposta(Usuario usuario) {
        UsuarioAuthDTO usuarioDTO = new UsuarioAuthDTO(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getRole()
        );

        String accessToken = tokenService.gerarToken(usuario);
        String refreshToken = refreshTokenService.gerar(usuario);

        return new LoginResponseDTO(accessToken, refreshToken, usuarioDTO);
    }
}
