package vexon.sellionpdv.maquininha;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vexon.sellionpdv.maquininha.dto.MaquininhaRequestDTO;
import vexon.sellionpdv.maquininha.dto.MaquininhaResponseDTO;

import java.util.List;

@RestController
@RequestMapping("/api/maquininhas")
@RequiredArgsConstructor
public class MaquininhaController {

    private final MaquininhaService service;

    @GetMapping
    public ResponseEntity<List<MaquininhaResponseDTO>> listarMaquininhas() {
        return ResponseEntity.ok(service.listarTodas());
    }

    @PostMapping
    public ResponseEntity<MaquininhaResponseDTO> cadastrarMaquininha(
            @RequestBody @Valid MaquininhaRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.cadastrar(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MaquininhaResponseDTO> atualizarMaquininha(
            @PathVariable Long id,
            @RequestBody @Valid MaquininhaRequestDTO dto) {
        return ResponseEntity.ok(service.atualizar(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> inativarMaquininha(@PathVariable Long id) {
        service.inativar(id);
        return ResponseEntity.noContent().build();
    }
}