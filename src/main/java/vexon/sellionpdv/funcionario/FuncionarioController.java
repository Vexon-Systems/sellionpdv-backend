package vexon.sellionpdv.funcionario;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import vexon.sellionpdv.funcionario.dto.FuncionarioAtualizacaoRequestDTO;
import vexon.sellionpdv.funcionario.dto.FuncionarioRequestDTO;
import vexon.sellionpdv.funcionario.dto.FuncionarioResponseDTO;

import java.util.List;

@RestController
@RequestMapping("/api/funcionarios")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class FuncionarioController {

    private final FuncionarioService funcionarioService;

    @GetMapping
    public ResponseEntity<List<FuncionarioResponseDTO>> listar(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(funcionarioService.listarFuncionarios(userDetails.getUsername()));
    }

    @PostMapping
    public ResponseEntity<FuncionarioResponseDTO> criar(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid FuncionarioRequestDTO request) {
        FuncionarioResponseDTO response = funcionarioService.criarFuncionario(request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FuncionarioResponseDTO> atualizar(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid FuncionarioAtualizacaoRequestDTO request) {
        return ResponseEntity.ok(funcionarioService.atualizarFuncionario(id, request, userDetails.getUsername()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        funcionarioService.inativarFuncionario(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
