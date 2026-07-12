# Spec: Refresh Token

> **Status**: Concluído e validado em 2026-07-09 — migration V2 aplicada no banco de dev real; fluxo completo login → refresh → rotação → logout testado ponta a ponta via curl; access token confirmado expirando em ~15min (era 2h). 228/228 testes.
> Frontend atualizado em 2026-07-10 — consome `accessToken`/`refreshToken`, chama `/api/auth/refresh` em 401 e `/api/auth/logout` no logout. Bloqueante resolvido.
> **Esforço estimado**: ~4-6 horas (setup) + validação — a maior das specs de Tier 0/1 até agora
> **Prioridade**: Alta (Tier 1 — hoje o token dura 2h, sem renovação e sem revogação: se vazar, fica válido até expirar sozinho)

---

## 1. Resumo

Hoje o login gera um único JWT válido por 2 horas (`api.security.token.expiration-hours=2`). Não existe renovação (o usuário precisa logar de novo quando expira) nem revogação (não tem como invalidar um token antes da hora — "logout" hoje é só o frontend apagar o token localmente; o token continua válido no backend até expirar sozinho).

A correção padrão é o par **access token + refresh token**:
- **Access token**: JWT de vida curta (proponho **15 minutos**, reduzindo de 2h), do jeito que já existe hoje — vai no header `Authorization` de cada requisição.
- **Refresh token**: um valor opaco de vida longa (**30 dias**), guardado no banco (com hash, nunca em texto puro — mesma lógica de nunca guardar senha em texto puro), trocado por um novo access token quando o antigo expira, **sem precisar logar de novo**.

Isso muda o risco de "token vazado fica válido por até 2h" pra "access token vazado fica válido por até 15min, e o refresh token pode ser revogado a qualquer momento" (logout de verdade, não só client-side).

**Rotação de refresh token**: toda vez que um refresh token é usado, ele é invalidado e um novo é emitido junto com o novo access token. Isso significa que se alguém roubar um refresh token e usar antes do dono, o próximo uso do dono legítimo vai falhar (o token já foi rotacionado) — um sinal de que algo está errado, sem precisar de infraestrutura extra pra detectar isso.

**Simplicidade deliberada**: não estou propondo blacklist de access tokens (continuam stateless, JWT puro, validados só pela assinatura — exatamente como hoje). A única coisa nova que fica no banco é o refresh token. Isso mantém o modelo mental simples: "o token curto não pode ser revogado antes da hora, mas dura pouco; o token longo pode ser revogado a qualquer momento, e é o único jeito de continuar logado sem senha".

---

## 2. Impacto no frontend (fora deste repo, mas precisa ser dito)

Essa mudança **quebra o contrato da API de login**: `LoginResponseDTO.token` vira `LoginResponseDTO.accessToken`, e um novo campo `refreshToken` aparece. O frontend precisa:
1. Guardar o `refreshToken` (ex.: `localStorage`, junto com o access token).
2. Ao receber `401` de qualquer chamada, tentar `POST /api/auth/refresh` com o refresh token antes de redirecionar pro login.
3. Chamar `POST /api/auth/logout` no botão de sair, não só apagar o token local.

Isso é trabalho de outro repositório — só estou deixando registrado aqui porque essa spec, sozinha, não entrega valor nenhum pro usuário final sem o ajuste correspondente no frontend.

---

## 3. Arquivos exatos criados/modificados

| Arquivo | Ação | O que muda |
|---|---|---|
| `src/main/resources/db/migration/V2__criar_refresh_tokens.sql` | **Criar** | Tabela `refresh_tokens` — primeira migration real desde o baseline (V1) |
| `src/main/java/vexon/sellionpdv/auth/RefreshToken.java` | **Criar** | Entidade JPA |
| `src/main/java/vexon/sellionpdv/auth/RefreshTokenRepository.java` | **Criar** | `JpaRepository<RefreshToken, Long>` + `findByTokenHash` |
| `src/main/java/vexon/sellionpdv/auth/RefreshTokenService.java` | **Criar** | Gerar, validar, rotacionar e revogar refresh tokens |
| `src/main/java/vexon/sellionpdv/auth/dto/RefreshRequestDTO.java` | **Criar** | Record com `refreshToken` (validado `@NotBlank`) |
| `src/main/java/vexon/sellionpdv/auth/dto/LogoutRequestDTO.java` | **Criar** | Idem |
| `src/main/java/vexon/sellionpdv/auth/dto/LoginResponseDTO.java` | Modificar | `token` → `accessToken`; novo campo `refreshToken` |
| `src/main/java/vexon/sellionpdv/auth/AuthService.java` | Modificar | `realizarLogin` passa a gerar os dois tokens; novos métodos `renovarToken` e `logout` |
| `src/main/java/vexon/sellionpdv/auth/AuthController.java` | Modificar | Novos endpoints `POST /api/auth/refresh` e `POST /api/auth/logout` |
| `src/main/java/vexon/sellionpdv/security/TokenService.java` | Modificar | `expiration-hours` (int) → `expiration-minutos` (int); default passa de 120 pra 15 |
| `src/main/java/vexon/sellionpdv/security/SecurityConfig.java` | Modificar | `/api/auth/refresh` e `/api/auth/logout` também `permitAll()` |
| `src/main/resources/application.properties` | Modificar | Renomeia `expiration-hours`, adiciona `api.security.refresh-token.expiration-days` |
| `src/test/resources/application.properties` | Modificar | Mesmo ajuste de nome de propriedade |
| `src/test/java/vexon/sellionpdv/auth/AuthServiceTest.java` | Modificar | `resultado.token()` → `resultado.accessToken()`; novos testes pra `renovarToken`/`logout` |

---

## 4. Dependências a instalar

**Nenhuma.** `java.security.MessageDigest` (SHA-256, pra hashear o refresh token antes de gravar) já é parte do JDK padrão.

---

## 5. Variáveis de ambiente

**Nenhuma nova.** Configuração por properties com default, igual ao rate limit:

```properties
# application.properties
api.security.token.expiration-minutos=15
api.security.refresh-token.expiration-dias=30
```

---

## 6. Modelagem — por que `RefreshToken` não tem `@TenantId`

Igual ao `Usuario` (ADR 001): quando o refresh token chega na requisição de `/api/auth/refresh`, **ainda não existe access token válido pra extrair o `tenantId`** — é exatamente o mesmo problema do login. Por isso `RefreshToken` é mapeado com `@ManyToOne` comum pro `Usuario` (que já carrega o `Tenant`), sem `@TenantId`, e a busca é feita pelo hash do token, não por tenant.

```sql
-- V2__criar_refresh_tokens.sql
CREATE TABLE public.refresh_tokens (
    id bigserial NOT NULL,
    usuario_id int8 NOT NULL,
    token_hash varchar(64) NOT NULL,
    expira_em timestamptz NOT NULL,
    revogado bool DEFAULT false NOT NULL,
    criado_em timestamptz DEFAULT now() NOT NULL,
    CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id),
    CONSTRAINT refresh_tokens_token_hash_key UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_usuario FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE CASCADE
);
CREATE INDEX idx_refresh_tokens_usuario ON public.refresh_tokens USING btree (usuario_id);
```

`token_hash varchar(64)`: tamanho exato de um hex de SHA-256 (32 bytes → 64 caracteres hex).

---

## 7. Desenho do código

```java
// auth/RefreshToken.java
package vexon.sellionpdv.auth;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import vexon.sellionpdv.usuario.Usuario;

import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(of = "id")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Builder.Default
    @Column(name = "revogado", nullable = false)
    private Boolean revogado = false;

    @CreationTimestamp
    @Column(name = "criado_em", updatable = false)
    private Instant criadoEm;
}
```

```java
// auth/RefreshTokenService.java
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
```

```java
// auth/AuthService.java — trechos alterados/novos
@Transactional
public LoginResponseDTO realizarLogin(LoginRequestDTO request) {
    Usuario usuario = usuarioRepository.findByEmailWithTenant(request.email())
            .orElseThrow(() -> new BusinessException("E-mail ou senha inválidos"));

    if (!passwordEncoder.matches(request.senha(), usuario.getSenhaHash()) || !usuario.getAtivo()) {
        throw new BusinessException("E-mail ou senha inválidos");
    }

    return montarResposta(usuario);
}

@Transactional
public LoginResponseDTO renovarToken(RefreshRequestDTO request) {
    RefreshToken tokenAntigo = refreshTokenService.validarERevogar(request.refreshToken());
    return montarResposta(tokenAntigo.getUsuario());
}

@Transactional
public void logout(LogoutRequestDTO request) {
    refreshTokenService.revogarPorToken(request.refreshToken());
}

private LoginResponseDTO montarResposta(Usuario usuario) {
    UsuarioAuthDTO usuarioDTO = new UsuarioAuthDTO(
            usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getRole());

    String accessToken = tokenService.gerarToken(usuario);
    String refreshToken = refreshTokenService.gerar(usuario);

    return new LoginResponseDTO(accessToken, refreshToken, usuarioDTO);
}
```

```java
// auth/AuthController.java — endpoints novos
@PostMapping("/refresh")
public ResponseEntity<LoginResponseDTO> refresh(@RequestBody @Valid RefreshRequestDTO request) {
    return ResponseEntity.ok(authService.renovarToken(request));
}

@PostMapping("/logout")
public ResponseEntity<Void> logout(@RequestBody @Valid LogoutRequestDTO request) {
    authService.logout(request);
    return ResponseEntity.noContent().build();
}
```

```java
// security/SecurityConfig.java — trecho alterado
req.requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll();
```

---

## 8. Passo a passo para testar localmente antes do deploy

### Passo 1 — Validar a migration V2 num banco descartável primeiro

Repetir o mesmo processo da spec do Flyway: criar um projeto Supabase descartável (ou reaproveitar um já existente), rodar a aplicação apontando pra ele, e confirmar no log que o Flyway aplica **duas** migrations em sequência:

```
Migrating schema "public" to version "1 - baseline schema"
Successfully applied 1 migration...
Migrating schema "public" to version "2 - criar refresh tokens"
Successfully applied 2 migrations...
```

Essa é a primeira vez que uma migration real (não-baseline) é testada de ponta a ponta — vale conferir com atenção que não há erro de sintaxe SQL nem conflito de nome de constraint.

### Passo 2 — Baseline/migrate no banco de dev real

Só depois do Passo 1 validado, rodar contra o banco de dev de verdade — dessa vez vai ser um **migrate de verdade** (não um baseline, já que a versão 1 já está baselineada), criando a tabela `refresh_tokens` no banco de dev.

### Passo 3 — Testar o fluxo completo de login → refresh → logout

```bash
# Login
RESPOSTA=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@sellion.com.br","senha":"admin123"}')
echo "$RESPOSTA"

REFRESH_TOKEN=$(echo "$RESPOSTA" | grep -o '"refreshToken":"[^"]*' | cut -d'"' -f4)

# Refresh — deve retornar novo par de tokens
curl -s -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}"
```

Confirmar que a resposta do refresh tem um `accessToken` e `refreshToken` **diferentes** dos originais.

### Passo 4 — Confirmar que o refresh token antigo não funciona mais (rotação)

Repetir a chamada de refresh do Passo 3 com o **mesmo `$REFRESH_TOKEN` original** (já usado uma vez). Deve retornar erro (`BusinessException` → 422) "Sessão expirada. Faça login novamente." — confirma que a rotação está funcionando.

### Passo 5 — Testar logout

Fazer login de novo, pegar um refresh token novo, chamar `/api/auth/logout` com ele, e depois tentar `/api/auth/refresh` com o mesmo token — deve falhar (revogado).

### Passo 6 — Confirmar expiração do access token

Com `expiration-minutos=15`, não dá pra esperar 15 minutos todo teste. Pra validar rápido, trocar temporariamente pra `1` minuto local, gerar um token, esperar passar de 1 minuto, e confirmar que uma chamada autenticada com esse token retorna 401/403. Reverter o valor antes de seguir.

### Passo 7 — Rodar a suíte de testes

```bash
./mvnw test
```

`AuthServiceTest` precisa de ajuste (`resultado.token()` → `resultado.accessToken()`, novo mock de `RefreshTokenService`) — isso é esperado, não é regressão. Adicionar testes novos pra `renovarToken` (sucesso, token expirado, token revogado, token inexistente) e `logout`.

### Passo 8 — Deploy

Nenhuma variável de ambiente nova. A migration `V2` roda sozinha no boot, igual toda migration Flyway daqui pra frente.

---

## 9. Riscos e mitigações

| Risco | Mitigação |
|---|---|
| Frontend não atualizado quebra login (contrato da API muda) | Coordenar deploy backend+frontend juntos; considerar manter `token` como alias de `accessToken` por um tempo se o deploy não puder ser simultâneo (não incluído nesta spec — avaliar se necessário) |
| Tabela `refresh_tokens` cresce indefinidamente (nunca é limpa) | Aceitável no volume atual — cada linha é pequena. Se virar problema, um job de limpeza (deletar `revogado=true` ou `expira_em` no passado há mais de X dias) é uma spec futura simples, não precisa resolver agora |
| Rate limit do login (Tier 1 anterior) não cobre `/api/auth/refresh` | Fora do escopo desta spec — o refresh exige posse do refresh token (não é adivinhável por força bruta como senha), risco bem menor. Documentado, não implementado |
| Refresh token em texto puro no `localStorage` do navegador (decisão do frontend, fora deste repo) | Mesma exposição que o access token já tem hoje; mitigada pela rotação (uso indevido invalida o token pro dono legítimo, sinalizando o problema) |
