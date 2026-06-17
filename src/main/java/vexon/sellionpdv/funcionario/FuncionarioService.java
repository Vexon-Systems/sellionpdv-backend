package vexon.sellionpdv.funcionario;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vexon.sellionpdv.common.exception.BusinessException;
import vexon.sellionpdv.common.exception.ResourceNotFoundException;
import vexon.sellionpdv.funcionario.dto.FuncionarioAtualizacaoRequestDTO;
import vexon.sellionpdv.funcionario.dto.FuncionarioRequestDTO;
import vexon.sellionpdv.funcionario.dto.FuncionarioResponseDTO;
import vexon.sellionpdv.usuario.Usuario;
import vexon.sellionpdv.usuario.UsuarioRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FuncionarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<FuncionarioResponseDTO> listarFuncionarios(String emailLogado) {
        Usuario usuarioLogado = buscarUsuarioLogado(emailLogado);
        return usuarioRepository.findAllByTenantAndAtivoTrueOrderByIdAsc(usuarioLogado.getTenant())
                .stream()
                .map(this::mapearParaDTO)
                .toList();
    }

    @Transactional
    public FuncionarioResponseDTO criarFuncionario(FuncionarioRequestDTO request, String emailLogado) {
        Usuario usuarioLogado = buscarUsuarioLogado(emailLogado);

        if (usuarioRepository.existsByEmailAndAtivoTrue(request.email())) {
            throw new BusinessException("Já existe um usuário ativo cadastrado com este e-mail.");
        }

        Usuario novoFuncionario = Usuario.builder()
                .nome(request.nome())
                .email(request.email())
                .senhaHash(passwordEncoder.encode(request.senha()))
                .role("ROLE_" + request.role())
                .ativo(true)
                .tenant(usuarioLogado.getTenant())
                .build();

        return mapearParaDTO(usuarioRepository.save(novoFuncionario));
    }

    @Transactional
    public FuncionarioResponseDTO atualizarFuncionario(Long id, FuncionarioAtualizacaoRequestDTO request, String emailLogado) {
        Usuario usuarioLogado = buscarUsuarioLogado(emailLogado);

        Usuario funcionario = usuarioRepository.findByIdAndTenantAndAtivoTrue(id, usuarioLogado.getTenant())
                .orElseThrow(() -> new ResourceNotFoundException("Funcionário não encontrado."));

        funcionario.setNome(request.nome());
        funcionario.setRole("ROLE_" + request.role());

        return mapearParaDTO(usuarioRepository.save(funcionario));
    }

    @Transactional
    public void inativarFuncionario(Long id, String emailLogado) {
        Usuario usuarioLogado = buscarUsuarioLogado(emailLogado);

        if (usuarioLogado.getId().equals(id)) {
            throw new BusinessException("Você não pode inativar sua própria conta.");
        }

        Usuario funcionario = usuarioRepository.findByIdAndTenantAndAtivoTrue(id, usuarioLogado.getTenant())
                .orElseThrow(() -> new ResourceNotFoundException("Funcionário não encontrado."));

        funcionario.setAtivo(false);
        funcionario.setEmail("deleted_" + id + "_" + funcionario.getEmail());
    }

    private Usuario buscarUsuarioLogado(String email) {
        return usuarioRepository.findByEmailWithTenant(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário autenticado não encontrado."));
    }

    private FuncionarioResponseDTO mapearParaDTO(Usuario usuario) {
        return new FuncionarioResponseDTO(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getRole().replace("ROLE_", ""),
                usuario.getAtivo()
        );
    }
}
