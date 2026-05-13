package vexon.sellionpdv.Venda;

import org.springframework.data.jpa.repository.JpaRepository;
import vexon.sellionpdv.caixa.Caixa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VendaRepository
        extends JpaRepository<Venda, Long> {

    List<Venda> findByCaixa(
            Caixa caixa
    );

    Optional<Venda> findByIdempotencyKey(
            UUID idempotencyKey
    );
}