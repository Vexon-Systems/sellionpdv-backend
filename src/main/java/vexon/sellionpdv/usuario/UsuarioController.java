package vexon.sellionpdv.usuario;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vexon.sellionpdv.usuario.dto.*;

import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping("/me")
    public ResponseEntity<UsuarioMeResponseDTO> obterMe(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(usuarioService.obterMe(userDetails.getUsername()));
    }

    @PutMapping("/me")
    public ResponseEntity<UsuarioMeResponseDTO> atualizarDados(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid UsuarioAtualizacaoRequestDTO request) {
        return ResponseEntity.ok(usuarioService.atualizarDados(userDetails.getUsername(), request));
    }

    @PutMapping("/me/senha")
    public ResponseEntity<Void> atualizarSenha(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid UsuarioSenhaRequestDTO request) {
        usuarioService.atualizarSenha(userDetails.getUsername(), request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/me/preferencias")
    public ResponseEntity<UsuarioPreferenciasResponseDTO> atualizarPreferencias(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid UsuarioPreferenciasRequestDTO request) {
        return ResponseEntity.ok(usuarioService.atualizarPreferencias(userDetails.getUsername(), request));
    }

    @PutMapping("/me/pin")
    public ResponseEntity<UsuarioPreferenciasResponseDTO> atualizarPin(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid UsuarioPinRequestDTO request) {
        return ResponseEntity.ok(usuarioService.atualizarPin(userDetails.getUsername(), request));
    }

    @PostMapping("/me/avatar")
    public ResponseEntity<Map<String, String>> uploadAvatar(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(usuarioService.uploadAvatar(userDetails.getUsername(), file));
    }
}