package vexon.sellionpdv.caixa;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vexon.sellionpdv.caixa.dto.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Service
public class CaixaService {

    private final CaixaRepository repository;

    public CaixaService(CaixaRepository repository) {
        this.repository = repository;
    }

    public CaixaResponseDTO buscarCaixaAtual() {
        return repository.findByStatus("ABERTO")
                .map(this::mapToResponseDTO)
                .orElseThrow(() -> new RuntimeException("Nenhum caixa aberto encontrado."));
    }

    @Transactional
    public CaixaResponseDTO abrir(CaixaRequestDTO dto) {
        if (repository.existsByStatus("ABERTO")) {
            throw new RuntimeException("Não é permitido abrir um novo caixa enquanto houver um turno em aberto.");
        }

        Caixa caixa = new Caixa();
        caixa.setStatus("ABERTO");
        caixa.setSaldoInicial(dto.saldoInicial());
        caixa.setDataAbertura(LocalDateTime.now());

        Caixa salvo = repository.save(caixa);
        return mapToResponseDTO(salvo);
    }

    @Transactional
    public CaixaResponseDTO fechar(CaixaFechamentoRequestDTO dto) {
        Caixa caixa = repository.findByStatus("ABERTO")
                .orElseThrow(() -> new RuntimeException("Erro ao fechar: Não existe caixa aberto para encerramento."));

        // Lógica de Auditoria: O Backend calcula a diferença (furo) entre o esperado e o informado
        // Aqui você somaria (Saldo Inicial + Vendas - Sangrias + Reforços) para comparar
        BigDecimal saldoEsperado = caixa.getSaldoInicial(); // Simplificado para este exemplo
        BigDecimal furo = dto.saldoFinalInformado().subtract(saldoEsperado);

        caixa.setStatus("FECHADO");
        caixa.setDataFechamento(LocalDateTime.now());
        caixa.setSaldoFinalInformado(dto.saldoFinalInformado());
        caixa.setFuroCaixa(furo);

        return mapToResponseDTO(repository.save(caixa));
    }

    private CaixaResponseDTO mapToResponseDTO(Caixa caixa) {
        return new CaixaResponseDTO(
                caixa.getId(),
                caixa.getStatus(),
                caixa.getDataAbertura(),
                caixa.getSaldoInicial()
        );
    }
}