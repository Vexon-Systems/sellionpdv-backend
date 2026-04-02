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
}