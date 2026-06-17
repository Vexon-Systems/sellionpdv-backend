package vexon.sellionpdv.usuario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import vexon.sellionpdv.tenant.Tenant;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    @Query("SELECT u FROM Usuario u JOIN FETCH u.tenant WHERE u.email = :email")
    Optional<Usuario> findByEmailWithTenant(String email);

    List<Usuario> findAllByTenantAndAtivoTrueOrderByIdAsc(Tenant tenant);

    boolean existsByEmailAndAtivoTrue(String email);

    Optional<Usuario> findByIdAndTenantAndAtivoTrue(Long id, Tenant tenant);
}
