package vexon.sellionpdv.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import vexon.sellionpdv.usuario.Usuario;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByUsuarioAndRevogadoFalse(Usuario usuario);
}
