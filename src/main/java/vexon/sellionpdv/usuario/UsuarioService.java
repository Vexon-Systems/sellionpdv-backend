package vexon.sellionpdv.usuario;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vexon.sellionpdv.usuario.dto.*;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    // Busca o usuário logado usando o e-mail extraído do token JWT
    private Usuario buscarUsuarioLogadoSeguro(String email) {
        return usuarioRepository.findByEmailWithTenant(email)
                .orElseThrow(() -> new RuntimeException("Utilizador não encontrado ou não pertence a esta Franquia"));
    }

    @Transactional(readOnly = true)
    public UsuarioMeResponseDTO obterMe(String email) {
        Usuario usuario = buscarUsuarioLogadoSeguro(email);
        return mapearParaDTO(usuario);
    }

    @Transactional
    public UsuarioMeResponseDTO atualizarDados(String email, UsuarioAtualizacaoRequestDTO dto) {
        Usuario usuario = buscarUsuarioLogadoSeguro(email);
        usuario.setNome(dto.nome());
        usuario.setTelefone(dto.telefone());

        return mapearParaDTO(usuarioRepository.save(usuario));
    }

    @Transactional
    public void atualizarSenha(String email, UsuarioSenhaRequestDTO dto) {
        Usuario usuario = buscarUsuarioLogadoSeguro(email);

        if (!passwordEncoder.matches(dto.senhaAtual(), usuario.getSenhaHash())) {
            throw new RuntimeException("A senha atual informada está incorreta.");
        }

        usuario.setSenhaHash(passwordEncoder.encode(dto.novaSenha()));
        usuarioRepository.save(usuario);
    }

    @Transactional
    public UsuarioPreferenciasResponseDTO atualizarPreferencias(String email, UsuarioPreferenciasRequestDTO dto) {
        Usuario usuario = buscarUsuarioLogadoSeguro(email);
        UsuarioPreferencias pref = usuario.getPreferencias();

        if (pref == null) {
            pref = new UsuarioPreferencias();
            usuario.setPreferencias(pref);
        }

        pref.setTema(dto.tema());
        pref.setSonsAtivos(dto.sonsAtivos());
        pref.setTamanhoInterface(dto.tamanhoInterface());

        usuarioRepository.save(usuario);

        return new UsuarioPreferenciasResponseDTO(
                pref.getTema(), pref.getSonsAtivos(), pref.getTamanhoInterface(), pref.getUsaPin());
    }

    @Transactional
    public UsuarioPreferenciasResponseDTO atualizarPin(String email, UsuarioPinRequestDTO dto) {
        Usuario usuario = buscarUsuarioLogadoSeguro(email);
        UsuarioPreferencias pref = usuario.getPreferencias();

        if (pref == null) {
            pref = new UsuarioPreferencias();
            usuario.setPreferencias(pref);
        }

        if (dto.pin() == null || dto.pin().trim().isEmpty()) {
            pref.setUsaPin(false);
            pref.setPinHash(null);
        } else {
            pref.setUsaPin(true);
            pref.setPinHash(passwordEncoder.encode(dto.pin())); // Hash usando Argon2id
        }

        usuarioRepository.save(usuario);

        return new UsuarioPreferenciasResponseDTO(
                pref.getTema(), pref.getSonsAtivos(), pref.getTamanhoInterface(), pref.getUsaPin());
    }

    // Mapeamento manual rápido. Recomendo usar MapStruct se o projeto escalar muito.
    private UsuarioMeResponseDTO mapearParaDTO(Usuario usuario) {
        UsuarioPreferencias pref = usuario.getPreferencias();
        UsuarioPreferenciasResponseDTO prefDTO = null;

        if (pref != null) {
            prefDTO = new UsuarioPreferenciasResponseDTO(
                    pref.getTema(), pref.getSonsAtivos(), pref.getTamanhoInterface(), pref.getUsaPin());
        }

        return new UsuarioMeResponseDTO(
                usuario.getId(),
                usuario.getTenant().getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getTelefone(),
                usuario.getRole(),
                usuario.getAvatarUrl(),
                prefDTO
        );
    }
}