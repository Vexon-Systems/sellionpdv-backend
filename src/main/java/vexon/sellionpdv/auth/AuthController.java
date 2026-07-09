package vexon.sellionpdv.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vexon.sellionpdv.auth.dto.LoginRequestDTO;
import vexon.sellionpdv.auth.dto.LoginResponseDTO;
import vexon.sellionpdv.auth.dto.LogoutRequestDTO;
import vexon.sellionpdv.auth.dto.RefreshRequestDTO;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody @Valid LoginRequestDTO request) {

        LoginResponseDTO response = authService.realizarLogin(request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponseDTO> refresh(@RequestBody @Valid RefreshRequestDTO request) {
        return ResponseEntity.ok(authService.renovarToken(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody @Valid LogoutRequestDTO request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }
}