package vexon.sellionpdv.caixa;
import vexon.sellionpdv.caixa.dto.FechamentoCaixaResponseDTO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vexon.sellionpdv.caixa.dto.*;

@RestController
@RequestMapping("/api/caixa")
public class CaixaController {

    private final CaixaService service;

    public CaixaController(
            CaixaService service
    ) {
        this.service = service;
    }

    @GetMapping("/atual")
    public ResponseEntity<vexon.sellionpdv.caixa.dto.CaixaResponseDTO>
    buscarCaixaAtual() {

        Caixa caixa =
                service.buscarCaixaAtual();

        return ResponseEntity.ok(
                new vexon.sellionpdv.caixa.dto.CaixaResponseDTO(caixa)
        );
    }

    @PostMapping("/abrir")
    public ResponseEntity<vexon.sellionpdv.caixa.dto.CaixaResponseDTO>
    abrirCaixa(
            @RequestBody
            @Valid
            vexon.sellionpdv.caixa.dto.CaixaRequestDTO dto
    ) {

        Caixa caixa =
                service.abrirCaixa(dto);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        new vexon.sellionpdv.caixa.dto.CaixaResponseDTO(caixa)
                );
    }

    @PostMapping("/movimentacao")
    public ResponseEntity<Void>
    registrarMovimentacao(
            @RequestBody
            @Valid
            MovimentacaoCaixaRequestDTO dto
    ) {

        service.registrarMovimentacao(
                dto
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .build();
    }

    @PostMapping("/fechar")
    public ResponseEntity<
            FechamentoCaixaResponseDTO
            >
    fecharCaixa(
            @RequestBody
            @Valid
            vexon.sellionpdv.caixa.dto.CaixaFechamentoRequestDTO dto
    ) {

        return ResponseEntity.ok(
                service.fecharCaixa(dto)
        );
    }
}