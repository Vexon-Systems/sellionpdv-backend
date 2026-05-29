package vexon.sellionpdv.produto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, Long> {
    boolean existsByNomeIgnoreCase(String nome);

    List<Produto> findAllByAtivoTrue();

    @Query("SELECT p FROM Produto p JOIN FETCH p.categoria WHERE p.ativo = true")
    List<Produto> buscarCatalogoAtivoComCategoria();

    List<Produto> findByGruposModificadoresId(Long id);
}
