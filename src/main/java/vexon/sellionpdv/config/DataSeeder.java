package vexon.sellionpdv.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import vexon.sellionpdv.tenant.Tenant;
import vexon.sellionpdv.tenant.TenantRepository;
import vexon.sellionpdv.usuario.Usuario;
import vexon.sellionpdv.usuario.UsuarioRepository;

@Configuration
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final TenantRepository tenantRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // Se já existe algum usuário no banco não fazemos nada para não duplicar
        if (usuarioRepository.count() > 0) {
            System.out.println("Usuário de teste já criado! Use para entrar: admin@sellion.com.br / admin123");
            return;
        }

        System.out.println("Banco de dados vazio. Semeando dados iniciais para teste...");

        // Criando o Restaurante Teste
        Tenant tenantTeste = Tenant.builder()
                .nomeFantasia("Sorveteria Teste Sellion")
                .cnpj("00000000000191")
                .ativo(true)
                .build();
        tenantRepository.save(tenantTeste);

        // Criando o Usuário Admin
        Usuario admin = Usuario.builder()
                .nome("Administrador do Sistema")
                .email("admin@sellion.com.br")
                .senhaHash(passwordEncoder.encode("admin123"))
                .role("ROLE_ADMIN")
                .ativo(true)
                .tenant(tenantTeste)
                .build();
        usuarioRepository.save(admin);

        System.out.println("Usuário de Teste criado! Você pode logar com: admin@sellion.com.br / admin123");
    }
}