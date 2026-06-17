package vexon.sellionpdv.usuario;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vexon.sellionpdv.common.exception.BusinessException;
import vexon.sellionpdv.common.exception.ResourceNotFoundException;
import vexon.sellionpdv.usuario.dto.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.uploads.base-url}")
    private String uploadsBaseUrl;

    private static final Map<String, String> MIME_PARA_EXTENSAO = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private Usuario buscarUsuarioLogadoSeguro(String email) {
        return usuarioRepository.findByEmailWithTenant(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilizador não encontrado ou não pertence a esta Franquia"));
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
            throw new BusinessException("A senha atual informada está incorreta.");
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
            pref.setPinHash(passwordEncoder.encode(dto.pin()));
        }

        usuarioRepository.save(usuario);

        return new UsuarioPreferenciasResponseDTO(
                pref.getTema(), pref.getSonsAtivos(), pref.getTamanhoInterface(), pref.getUsaPin());
    }

    @Transactional
    public Map<String, String> uploadAvatar(String email, MultipartFile file) {
        Usuario usuario = buscarUsuarioLogadoSeguro(email);

        if (file.isEmpty()) throw new BusinessException("Arquivo vazio.");
        if (file.getSize() > 5_242_880L) throw new BusinessException("Arquivo excede o tamanho máximo de 5 MB.");

        String extensao = MIME_PARA_EXTENSAO.get(file.getContentType());
        if (extensao == null) throw new BusinessException("Tipo de arquivo não permitido. Envie uma imagem JPEG, PNG ou WebP.");

        try {
            String nomeArquivo = UUID.randomUUID() + extensao;
            Path uploadPath = Paths.get("uploads");
            if (!Files.exists(uploadPath)) Files.createDirectories(uploadPath);
            Files.copy(file.getInputStream(), uploadPath.resolve(nomeArquivo), StandardCopyOption.REPLACE_EXISTING);

            String avatarUrl = uploadsBaseUrl + nomeArquivo;
            usuario.setAvatarUrl(avatarUrl);
            usuarioRepository.save(usuario);

            return Map.of("avatarUrl", avatarUrl);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao salvar a imagem. Tente novamente.");
        }
    }

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
