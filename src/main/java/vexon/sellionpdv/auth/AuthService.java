package vexon.sellionpdv.auth;

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

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public LoginResponseDTO realizarLogin(LoginRequestDTO request) {
        Usuario usuario = usuarioRepository.findByEmailWithTenant(request.email())
                .orElseThrow(() -> new BusinessException("E-mail ou senha inválidos"));

        if (!passwordEncoder.matches(request.senha(), usuario.getSenhaHash()) || !usuario.getAtivo()) {
            throw new BusinessException("E-mail ou senha inválidos");
        }

        return montarResposta(usuario);
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
