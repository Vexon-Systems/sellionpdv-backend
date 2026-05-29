package vexon.sellionpdv.usuario;

import vexon.sellionpdv.usuario.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    @Query("SELECT u FROM Usuario u JOIN FETCH u.tenant WHERE u.email = :email")
    Optional<Usuario> findByEmailWithTenant(String email);
}
