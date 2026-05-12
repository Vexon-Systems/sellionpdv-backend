package vexon.sellionpdv.caixa;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vexon.sellionpdv.caixa.dto.*;

@RestController
@RequestMapping("/api/caixa")
public class CaixaController {

    private final CaixaService service;

    public CaixaController(CaixaService service) {
        this.service = service;
    }

    @GetMapping("/atual")
    public ResponseEntity<CaixaResponseDTO> obterCaixaAtual() {
        return ResponseEntity.ok(service.buscarCaixaAtual());
    }

    @PostMapping("/abrir")
    public ResponseEntity<CaixaResponseDTO> abrirCaixa(@RequestBody CaixaRequestDTO dto) {
        return ResponseEntity.status(201).body(service.abrir(dto));
    }

    @PostMapping("/fechar")
    public ResponseEntity<CaixaResponseDTO> fecharCaixa(@RequestBody CaixaFechamentoRequestDTO dto) {
        return ResponseEntity.ok(service.fechar(dto));
    }
}