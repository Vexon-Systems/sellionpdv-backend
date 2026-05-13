package vexon.sellionpdv.caixa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimentacaoCaixaRepository
        extends JpaRepository<MovimentacaoCaixa, Long> {

    List<MovimentacaoCaixa> findByCaixa(
            Caixa caixa
    );
}