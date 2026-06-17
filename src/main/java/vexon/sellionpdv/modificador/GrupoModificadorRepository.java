package vexon.sellionpdv.modificador;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GrupoModificadorRepository extends JpaRepository<GrupoModificador, Long> {
    boolean existsByNomeIgnoreCaseAndAtivoTrue(String nome);

    List<GrupoModificador> findAllByAtivoTrue();
}
