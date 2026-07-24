package vexon.sellionpdv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * A API autentica exclusivamente pelo fluxo JWT próprio. Sem um
 * {@code UserDetailsService} ou {@code AuthenticationManager} da aplicação, o Spring Boot
 * criaria um usuário em memória e registraria uma senha aleatória no boot. A exclusão evita
 * esse caminho alternativo em todos os perfis, sem habilitar HTTP Basic ou form login.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class SellionpdvApplication {

	public static void main(String[] args) {
		SpringApplication.run(SellionpdvApplication.class, args);
	}

}
