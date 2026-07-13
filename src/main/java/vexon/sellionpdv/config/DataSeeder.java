package vexon.sellionpdv.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import vexon.sellionpdv.tenant.Tenant;
import vexon.sellionpdv.tenant.TenantRepository;
import vexon.sellionpdv.usuario.Usuario;
import vexon.sellionpdv.usuario.UsuarioRepository;

import java.util.UUID;

/**
 * Semeia um tenant e um usuário ADMIN de teste quando o banco está vazio.
 * Restrito a dev/test/local via {@code @Profile} — nunca deve existir como bean em
 * staging/prod, para não criar uma conta administrativa com senha previsível fora do
 * ambiente de desenvolvimento.
 */
@Configuration
@Profile({"dev", "test", "local"})
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final TenantRepository tenantRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // Se já existe algum usuário no banco não fazemos nada para não duplicar
        if (usuarioRepository.count() > 0) {
            log.info("Usuário de teste já existe, seed ignorado.");
            return;
        }

        log.info("Banco de dados vazio. Semeando dados iniciais para teste...");

        // Criando o Restaurante Teste
        Tenant tenantTeste = Tenant.builder()
                .nomeFantasia("Sorveteria Teste Sellion")
                .cnpj("00000000000191")
                .ativo(true)
                .build();
        tenantRepository.save(tenantTeste);

        // Senha gerada aleatoriamente a cada seed — nunca um valor fixo conhecido publicamente.
        String senhaGerada = UUID.randomUUID().toString();

        // Criando o Usuário Admin
        Usuario admin = Usuario.builder()
                .nome("Administrador do Sistema")
                .email("admin@sellion.com.br")
                .senhaHash(passwordEncoder.encode(senhaGerada))
                .role("ROLE_ADMIN")
                .ativo(true)
                .tenant(tenantTeste)
                .build();
        usuarioRepository.save(admin);

        log.info("Usuário de teste criado (admin@sellion.com.br). Senha gerada: {}", senhaGerada);
    }
}