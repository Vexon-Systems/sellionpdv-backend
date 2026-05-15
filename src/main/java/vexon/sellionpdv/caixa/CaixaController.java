package vexon.sellionpdv.caixa;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vexon.sellionpdv.caixa.dto.*;

@RestController
@RequestMapping("/api/caixa")
@RequiredArgsConstructor
public class CaixaController {

    private final CaixaService service;

    @GetMapping("/atual")
    public ResponseEntity<CaixaResponseDTO> buscarCaixaAtual() {
        Caixa caixa = service.buscarCaixaAtual();
        return ResponseEntity.ok(new CaixaResponseDTO(caixa));
    }

    @PostMapping("/abrir")
    public ResponseEntity<CaixaResponseDTO> abrirCaixa(@RequestBody @Valid CaixaRequestDTO dto) {
        Caixa caixa = service.abrirCaixa(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(new CaixaResponseDTO(caixa));
    }

    @PostMapping("/movimentacao")
    public ResponseEntity<Void> registrarMovimentacao(@RequestBody @Valid MovimentacaoCaixaRequestDTO dto) {
        service.registrarMovimentacao(dto);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/fechar")
    public ResponseEntity<CaixaFechamentoResponseDTO> fecharCaixa(@RequestBody @Valid CaixaFechamentoRequestDTO dto) {
        return ResponseEntity.ok(service.fecharCaixa(dto));
    }
}