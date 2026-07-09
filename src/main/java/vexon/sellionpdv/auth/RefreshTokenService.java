package vexon.sellionpdv.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vexon.sellionpdv.common.exception.BusinessException;
import vexon.sellionpdv.usuario.Usuario;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${api.security.refresh-token.expiration-dias}")
    private int expiracaoDias;

    @Transactional
    public String gerar(Usuario usuario) {
        String tokenBruto = UUID.randomUUID().toString();

        RefreshToken entidade = RefreshToken.builder()
                .usuario(usuario)
                .tokenHash(hash(tokenBruto))
                .expiraEm(Instant.now().plus(expiracaoDias, ChronoUnit.DAYS))
                .build();

        refreshTokenRepository.save(entidade);
        return tokenBruto;
    }

    @Transactional
    public RefreshToken validarERevogar(String tokenBruto) {
        RefreshToken entidade = refreshTokenRepository.findByTokenHash(hash(tokenBruto))
                .orElseThrow(() -> new BusinessException("Sessão expirada. Faça login novamente."));

        if (entidade.getRevogado() || entidade.getExpiraEm().isBefore(Instant.now())) {
            throw new BusinessException("Sessão expirada. Faça login novamente.");
        }

        entidade.setRevogado(true);
        return entidade;
    }

    @Transactional
    public void revogarPorToken(String tokenBruto) {
        refreshTokenRepository.findByTokenHash(hash(tokenBruto))
                .ifPresent(entidade -> entidade.setRevogado(true));
    }

    private String hash(String tokenBruto) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(tokenBruto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 deveria estar sempre disponível na JVM", e);
        }
    }
}
