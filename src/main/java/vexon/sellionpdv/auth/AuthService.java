package vexon.sellionpdv.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vexon.sellionpdv.auth.dto.LoginRequestDTO;
import vexon.sellionpdv.auth.dto.LoginResponseDTO;
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

    public LoginResponseDTO realizarLogin(LoginRequestDTO request) {
        Usuario usuario = usuarioRepository.findByEmailWithTenant(request.email())
                .orElseThrow(() -> new BusinessException("E-mail ou senha inválidos"));

        if (!passwordEncoder.matches(request.senha(), usuario.getSenhaHash()) || !usuario.getAtivo()) {
            throw new BusinessException("E-mail ou senha inválidos");
        }

        UsuarioAuthDTO usuarioDTO = new UsuarioAuthDTO(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getRole()
        );

        String tokenJwt = tokenService.gerarToken(usuario);

        return new LoginResponseDTO(tokenJwt, usuarioDTO);
    }
}
