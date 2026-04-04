package vexon.sellionpdv.modificador;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GrupoModificadorRepository extends JpaRepository<GrupoModificador, Long> {
    boolean existsByNomeIgnoreCase(String nome);
}
