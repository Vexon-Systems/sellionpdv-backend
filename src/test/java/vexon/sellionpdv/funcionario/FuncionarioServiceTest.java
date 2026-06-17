package vexon.sellionpdv.funcionario;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import vexon.sellionpdv.common.exception.BusinessException;
import vexon.sellionpdv.common.exception.ResourceNotFoundException;
import vexon.sellionpdv.funcionario.dto.FuncionarioAtualizacaoRequestDTO;
import vexon.sellionpdv.funcionario.dto.FuncionarioRequestDTO;
import vexon.sellionpdv.funcionario.dto.FuncionarioResponseDTO;
import vexon.sellionpdv.tenant.Tenant;
import vexon.sellionpdv.usuario.Usuario;
import vexon.sellionpdv.usuario.UsuarioRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FuncionarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private FuncionarioService funcionarioService;

    private Tenant tenantSimulado;
    private Usuario adminLogado;

    @BeforeEach
    void setUp() {
        tenantSimulado = Tenant.builder()
                .id(10L)
                .nomeFantasia("Sorveteria Teste")
                .build();

        adminLogado = Usuario.builder()
                .id(1L)
                .nome("Administrador")
                .email("admin@sellion.com.br")
                .role("ROLE_ADMIN")
                .ativo(true)
                .tenant(tenantSimulado)
                .build();
    }

    // --- GET /api/funcionarios ---

    @Test
    @DisplayName("Deve listar todos os funcionários ativos do tenant")
    void deveListarFuncionariosDoTenant() {
        Usuario funcionario1 = Usuario.builder()
                .id(2L).nome("João Silva").email("joao@sellion.com.br")
                .role("ROLE_OPERADOR").ativo(true).tenant(tenantSimulado).build();
        Usuario funcionario2 = Usuario.builder()
                .id(3L).nome("Maria Gestora").email("maria@sellion.com.br")
                .role("ROLE_ADMIN").ativo(true).tenant(tenantSimulado).build();

        when(usuarioRepository.findByEmailWithTenant("admin@sellion.com.br"))
                .thenReturn(Optional.of(adminLogado));
        when(usuarioRepository.findAllByTenantAndAtivoTrueOrderByIdAsc(tenantSimulado))
                .thenReturn(List.of(funcionario1, funcionario2));

        List<FuncionarioResponseDTO> resultado = funcionarioService.listarFuncionarios("admin@sellion.com.br");

        assertNotNull(resultado);
        assertEquals(2, resultado.size());
        assertEquals("João Silva", resultado.get(0).nome());
        assertEquals("OPERADOR", resultado.get(0).role());
        assertEquals("Maria Gestora", resultado.get(1).nome());
        assertEquals("ADMIN", resultado.get(1).role());
    }

    @Test
    @DisplayName("Deve retornar lista vazia quando não há funcionários ativos")
    void deveRetornarListaVaziaQuandoNaoHaFuncionarios() {
        when(usuarioRepository.findByEmailWithTenant("admin@sellion.com.br"))
                .thenReturn(Optional.of(adminLogado));
        when(usuarioRepository.findAllByTenantAndAtivoTrueOrderByIdAsc(tenantSimulado))
                .thenReturn(List.of());

        List<FuncionarioResponseDTO> resultado = funcionarioService.listarFuncionarios("admin@sellion.com.br");

        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
    }

    // --- POST /api/funcionarios ---

    @Test
    @DisplayName("Deve criar um funcionário com sucesso quando o e-mail não existir")
    void deveCriarFuncionarioComSucesso() {
        FuncionarioRequestDTO request = new FuncionarioRequestDTO(
                "Carlos Caixa", "carlos@sellion.com.br", "SenhaSegura123", "OPERADOR"
        );

        Usuario funcionarioSalvo = Usuario.builder()
                .id(3L).nome("Carlos Caixa").email("carlos@sellion.com.br")
                .role("ROLE_OPERADOR").ativo(true).tenant(tenantSimulado).build();

        when(usuarioRepository.findByEmailWithTenant("admin@sellion.com.br"))
                .thenReturn(Optional.of(adminLogado));
        when(usuarioRepository.existsByEmailAndAtivoTrue("carlos@sellion.com.br"))
                .thenReturn(false);
        when(passwordEncoder.encode("SenhaSegura123"))
                .thenReturn("$argon2id$hash");
        when(usuarioRepository.save(any(Usuario.class)))
                .thenReturn(funcionarioSalvo);

        FuncionarioResponseDTO response = funcionarioService.criarFuncionario(request, "admin@sellion.com.br");

        assertNotNull(response);
        assertEquals(3L, response.id());
        assertEquals("Carlos Caixa", response.nome());
        assertEquals("carlos@sellion.com.br", response.email());
        assertEquals("OPERADOR", response.role());
        assertTrue(response.ativo());
        verify(usuarioRepository, times(1)).save(any(Usuario.class));
        verify(passwordEncoder, times(1)).encode("SenhaSegura123");
    }

    @Test
    @DisplayName("Deve prefixar a role com ROLE_ ao salvar no banco")
    void deveSalvarComPrefixoRoleCorreto() {
        FuncionarioRequestDTO request = new FuncionarioRequestDTO(
                "Maria Admin", "maria@sellion.com.br", "Senha123", "ADMIN"
        );

        when(usuarioRepository.findByEmailWithTenant("admin@sellion.com.br"))
                .thenReturn(Optional.of(adminLogado));
        when(usuarioRepository.existsByEmailAndAtivoTrue("maria@sellion.com.br"))
                .thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$argon2id$hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> {
            Usuario u = invocation.getArgument(0);
            assertEquals("ROLE_ADMIN", u.getRole());
            return Usuario.builder().id(4L).nome(u.getNome()).email(u.getEmail())
                    .role(u.getRole()).ativo(true).tenant(tenantSimulado).build();
        });

        FuncionarioResponseDTO response = funcionarioService.criarFuncionario(request, "admin@sellion.com.br");

        assertEquals("ADMIN", response.role());
    }

    @Test
    @DisplayName("Deve lançar exceção ao tentar criar funcionário com e-mail já em uso")
    void naoDeveCriarFuncionarioComEmailDuplicado() {
        FuncionarioRequestDTO request = new FuncionarioRequestDTO(
                "Duplicado", "existente@sellion.com.br", "Senha123", "OPERADOR"
        );

        when(usuarioRepository.findByEmailWithTenant("admin@sellion.com.br"))
                .thenReturn(Optional.of(adminLogado));
        when(usuarioRepository.existsByEmailAndAtivoTrue("existente@sellion.com.br"))
                .thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                funcionarioService.criarFuncionario(request, "admin@sellion.com.br")
        );

        assertEquals("Já existe um usuário ativo cadastrado com este e-mail.", exception.getMessage());
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    // --- PUT /api/funcionarios/{id} ---

    @Test
    @DisplayName("Deve atualizar nome e role do funcionário com sucesso")
    void deveAtualizarFuncionarioComSucesso() {
        FuncionarioAtualizacaoRequestDTO request = new FuncionarioAtualizacaoRequestDTO(
                "Carlos Eduardo Caixa", "ADMIN"
        );

        Usuario funcionarioExistente = Usuario.builder()
                .id(3L).nome("Carlos Caixa").email("carlos@sellion.com.br")
                .role("ROLE_OPERADOR").ativo(true).tenant(tenantSimulado).build();

        when(usuarioRepository.findByEmailWithTenant("admin@sellion.com.br"))
                .thenReturn(Optional.of(adminLogado));
        when(usuarioRepository.findByIdAndTenantAndAtivoTrue(3L, tenantSimulado))
                .thenReturn(Optional.of(funcionarioExistente));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FuncionarioResponseDTO response = funcionarioService.atualizarFuncionario(3L, request, "admin@sellion.com.br");

        assertNotNull(response);
        assertEquals("Carlos Eduardo Caixa", response.nome());
        assertEquals("ADMIN", response.role());
        assertEquals("carlos@sellion.com.br", response.email());
        verify(usuarioRepository, times(1)).save(any(Usuario.class));
    }

    @Test
    @DisplayName("Deve lançar exceção ao tentar atualizar funcionário inexistente")
    void naoDeveAtualizarFuncionarioInexistente() {
        FuncionarioAtualizacaoRequestDTO request = new FuncionarioAtualizacaoRequestDTO(
                "Fantasma", "OPERADOR"
        );

        when(usuarioRepository.findByEmailWithTenant("admin@sellion.com.br"))
                .thenReturn(Optional.of(adminLogado));
        when(usuarioRepository.findByIdAndTenantAndAtivoTrue(999L, tenantSimulado))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () ->
                funcionarioService.atualizarFuncionario(999L, request, "admin@sellion.com.br")
        );

        assertEquals("Funcionário não encontrado.", exception.getMessage());
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    @DisplayName("Não deve permitir atualizar funcionário de outro tenant")
    void naoDeveAtualizarFuncionarioDeOutroTenant() {
        FuncionarioAtualizacaoRequestDTO request = new FuncionarioAtualizacaoRequestDTO(
                "Invasor", "OPERADOR"
        );

        when(usuarioRepository.findByEmailWithTenant("admin@sellion.com.br"))
                .thenReturn(Optional.of(adminLogado));
        when(usuarioRepository.findByIdAndTenantAndAtivoTrue(5L, tenantSimulado))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                funcionarioService.atualizarFuncionario(5L, request, "admin@sellion.com.br")
        );

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    // --- DELETE /api/funcionarios/{id} ---

    @Test
    @DisplayName("Deve inativar funcionário aplicando soft delete e anonimizando o e-mail")
    void deveInativarFuncionarioComSoftDelete() {
        Usuario funcionarioAtivo = Usuario.builder()
                .id(3L).nome("Carlos Caixa").email("carlos@sellion.com.br")
                .role("ROLE_OPERADOR").ativo(true).tenant(tenantSimulado).build();

        when(usuarioRepository.findByEmailWithTenant("admin@sellion.com.br"))
                .thenReturn(Optional.of(adminLogado));
        when(usuarioRepository.findByIdAndTenantAndAtivoTrue(3L, tenantSimulado))
                .thenReturn(Optional.of(funcionarioAtivo));

        funcionarioService.inativarFuncionario(3L, "admin@sellion.com.br");

        assertFalse(funcionarioAtivo.getAtivo());
        assertTrue(funcionarioAtivo.getEmail().startsWith("deleted_3_"));
        verify(usuarioRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Não deve permitir que o usuário inative a própria conta")
    void naoDevePermitirAutoExclusao() {
        when(usuarioRepository.findByEmailWithTenant("admin@sellion.com.br"))
                .thenReturn(Optional.of(adminLogado));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                funcionarioService.inativarFuncionario(1L, "admin@sellion.com.br")
        );

        assertEquals("Você não pode inativar sua própria conta.", exception.getMessage());
        verify(usuarioRepository, never()).findByIdAndTenantAndAtivoTrue(any(), any());
    }

    @Test
    @DisplayName("Deve lançar exceção ao tentar inativar funcionário inexistente")
    void naoDeveInativarFuncionarioInexistente() {
        when(usuarioRepository.findByEmailWithTenant("admin@sellion.com.br"))
                .thenReturn(Optional.of(adminLogado));
        when(usuarioRepository.findByIdAndTenantAndAtivoTrue(999L, tenantSimulado))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () ->
                funcionarioService.inativarFuncionario(999L, "admin@sellion.com.br")
        );

        assertEquals("Funcionário não encontrado.", exception.getMessage());
        verify(usuarioRepository, never()).deleteById(any());
    }
}
