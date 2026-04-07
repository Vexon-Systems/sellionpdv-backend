package vexon.sellionpdv.modificador;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vexon.sellionpdv.modificador.dto.*;

import java.util.List;
@RestController
@RequestMapping("/api/modificadores")
@RequiredArgsConstructor
public class ModificadorController {

    private final ModificadorService modificadorService;

    @PostMapping
    public ResponseEntity<GrupoResponseDTO> criar(@RequestBody @Valid GrupoRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(modificadorService.criarGrupo(request));
    }

    @GetMapping
    public ResponseEntity<List<GrupoResponseDTO>> listar() {
        return ResponseEntity.ok(modificadorService.listarGrupos());
    }

    @PutMapping("/{id}")
    public ResponseEntity<GrupoResponseDTO> atualizar(
            @PathVariable Long id,
            @RequestBody @Valid GrupoRequestDTO request) {
        return ResponseEntity.ok(modificadorService.atualizarGrupo(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        modificadorService.deletarGrupo(id);
        return ResponseEntity.noContent().build();
    }
}
