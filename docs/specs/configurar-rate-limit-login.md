# Spec: Rate Limit no Login

> **Status**: Concluído e validado em 2026-07-09 — testado localmente com curl em rajada: bloqueia na 6ª tentativa (429), inclusive com credenciais válidas, e se recupera gradualmente. 225/225 testes intactos.
> **Esforço estimado**: ~2 horas (setup) + validação
> **Prioridade**: Alta (Tier 1 — hoje `/api/auth/login` está `permitAll()` sem nenhuma proteção contra força bruta)
>
> **Correções feitas durante a validação real** (documentadas nas seções 6.3-6.5): as tentativas bloqueadas por
> credencial inválida retornam **422**, não 401 como a spec original assumia (mapeamento de `BusinessException`
> no `GlobalExceptionHandler`); e o `Refill.greedy` do Bucket4j repõe tokens continuamente, não em janelas fixas —
> testar em rajada (loop sem pausa), não com pausas manuais entre chamadas.

---

## 1. Resumo

Hoje `POST /api/auth/login` aceita chamadas ilimitadas. O Argon2id já torna cada tentativa de senha computacionalmente cara (não é um `MD5` que se testa milhões de vezes por segundo), mas isso sozinho não impede alguém de tentar centenas de e-mails/senhas por hora contra o mesmo endpoint — só torna mais lento, não impossível.

A correção é um **rate limit simples por IP**: depois de N tentativas de login num intervalo de tempo, o endpoint passa a responder `429 Too Many Requests` até o contador resetar. Isso é feito com um filtro que roda **antes** da requisição chegar no `AuthController` — nem chega a gastar CPU com Argon2 nas tentativas bloqueadas.

**Escolha de biblioteca**: [Bucket4j](https://github.com/bucket4j/bucket4j) — implementa o algoritmo *token bucket* (o padrão de facto pra rate limit em Java) sem precisar de Redis/banco externo: o estado fica em memória, num `ConcurrentHashMap` dentro do próprio processo. Isso é suficiente pro estágio atual (uma instância só rodando). Diferente do Flyway/Sentry, o Bucket4j **não tem autoconfiguração própria do Spring** — é só uma biblioteca de cálculo que a gente usa dentro de um filtro escrito à mão, então não tem risco de armadilha de versão do Boot 4 (já testei a resolução da dependência).

**Limitação a documentar, não a resolver agora**: como o estado é em memória, ele reseta se a aplicação reiniciar, e **não funciona corretamente se um dia rodarem duas instâncias do backend ao mesmo tempo** (cada instância teria seu próprio contador, dobrando o limite efetivo). Isso só vira problema se/quando o sistema escalar horizontalmente — nesse momento, a solução correta passa a ser um rate limit compartilhado (Redis). Não vale a complexidade disso agora.

---

## 2. Arquivos exatos criados/modificados

| Arquivo | Ação | O que muda |
|---|---|---|
| `pom.xml` | Modificar | Adiciona 1 dependência (`bucket4j-core`) |
| `src/main/java/vexon/sellionpdv/security/LoginRateLimitFilter.java` | **Criar** | Filtro que aplica o rate limit só em `POST /api/auth/login` |
| `src/main/java/vexon/sellionpdv/security/SecurityConfig.java` | Modificar | Registra o novo filtro na cadeia, antes do `SecurityFilter` |
| `src/main/resources/application.properties` | Modificar | Adiciona `security.rate-limit.login.*` (com default, sem variável de ambiente obrigatória) |

---

## 3. Dependências a instalar

```xml
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>8.10.1</version>
</dependency>
```

---

## 4. Variáveis de ambiente

**Nenhuma obrigatória.** Os limites ficam configuráveis via `application.properties`, com valores padrão sensatos — não é segredo, é ajuste de comportamento:

```properties
security.rate-limit.login.capacidade=5
security.rate-limit.login.janela-minutos=1
```

Ou seja: **5 tentativas por minuto por IP**, por padrão. Se um dia precisar afinar isso em produção sem recompilar, dá pra promover pra variável de ambiente depois — não é necessário agora.

---

## 5. Desenho do código

```java
// security/LoginRateLimitFilter.java
package vexon.sellionpdv.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${security.rate-limit.login.capacidade}")
    private int capacidade;

    @Value("${security.rate-limit.login.janela-minutos}")
    private int janelaMinutos;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        boolean isLogin = "POST".equalsIgnoreCase(request.getMethod())
                && "/api/auth/login".equals(request.getRequestURI());

        if (!isLogin) {
            filterChain.doFilter(request, response);
            return;
        }

        Bucket bucket = buckets.computeIfAbsent(request.getRemoteAddr(), ip ->
                Bucket.builder()
                        .addLimit(Bandwidth.classic(capacidade,
                                Refill.greedy(capacidade, Duration.ofMinutes(janelaMinutos))))
                        .build());

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }

        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS,
                "Muitas tentativas de login. Aguarde um minuto antes de tentar novamente.");
        detail.setTitle("Limite de tentativas excedido");

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), detail);
    }
}
```

```java
// security/SecurityConfig.java — trecho alterado
private final SecurityFilter securityFilter;
private final LoginRateLimitFilter loginRateLimitFilter;

// ...

return http
        // ...
        .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
```

**Por que checar path + método dentro do filtro em vez de mapear o filtro só pra `/api/auth/login`**: o Spring Security registra filtros pra cadeia inteira; restringir por URL exigiria uma segunda `SecurityFilterChain` só pra essa rota, adicionando complexidade desproporcional ao problema. Um `if` no início do filtro é mais simples de ler e manter.

**Por que `request.getRemoteAddr()` e não um header tipo `X-Forwarded-For`**: em produção, atrás de um proxy/load balancer (Railway, Render, etc.), o IP real do cliente normalmente vem no header `X-Forwarded-For`, não no `RemoteAddr` (que seria o IP do proxy interno). **Isso é uma limitação conhecida que fica documentada aqui, não resolvida agora** — vale revisitar quando o Tier 1 config de deploy/staging estiver definido, porque a forma certa de ler esse header depende de qual proxy for usado (nem todo `X-Forwarded-For` é confiável — pode ser forjado pelo próprio cliente se o proxy não sobrescrever). Por enquanto, `RemoteAddr` já resolve o caso de uso local/dev e o caso de deploy direto sem proxy.

---

## 6. Passo a passo para testar localmente antes do deploy

### Passo 1 — Rodar a aplicação normalmente

```bash
./mvnw spring-boot:run
```

### Passo 2 — Confirmar que as primeiras tentativas passam normalmente

```bash
for i in 1 2 3 4 5; do
  curl -s -o /dev/null -w "Tentativa $i: %{http_code}\n" \
    -X POST http://localhost:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email":"nao-existe@teste.com","senha":"errada"}'
done
```

Todas as 5 devem retornar **422** (credenciais inválidas mapeadas como `BusinessException` pelo `GlobalExceptionHandler` — comportamento normal, o rate limit ainda não bloqueou nada; não é 401 como se poderia supor).

### Passo 3 — Confirmar que a 6ª tentativa é bloqueada

```bash
curl -s -o /dev/null -w "Tentativa 6: %{http_code}\n" \
  -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"nao-existe@teste.com","senha":"errada"}'
```

Deve retornar **429**, com corpo:
```json
{
  "type": "about:blank",
  "title": "Limite de tentativas excedido",
  "status": 429,
  "detail": "Muitas tentativas de login. Aguarde um minuto antes de tentar novamente."
}
```

### Passo 4 — Confirmar que login legítimo também é bloqueado durante o limite

Repetir o Passo 3 **imediatamente em sequência** (sem pausa) com credenciais válidas (usuário seed `admin@sellion.com.br` / `admin123`, se estiver usando banco local/dev com o seed ativo). Deve continuar retornando **429** — o bloqueio é por IP, não por resultado da tentativa anterior.

> **Atenção ao tempo entre requisições**: o `Refill.greedy` do Bucket4j repõe tokens **continuamente** ao longo da janela (com capacidade 5 por minuto, ~1 token a cada 12s), não tudo de uma vez só no fim do minuto. Se você pausar pra ler o resultado entre uma chamada e outra, pode passar tempo suficiente pra um token já ter sido reposto e a tentativa seguinte passar — isso não é bug, é o comportamento correto do algoritmo. Pra validar o bloqueio de forma confiável, dispare as chamadas em rajada (num loop `for`, sem pausa manual entre elas), como no Passo 2.

### Passo 5 — Confirmar que o limite se recupera com o tempo

Esperar ~15 segundos e tentar de novo — como o refill é contínuo, não precisa esperar o minuto inteiro pra conseguir logar de novo, só o suficiente pra pelo menos 1 token voltar. Repetir o Passo 2 seguido de bloqueio (Passo 3) mostra o padrão: consome rápido, bloqueia, recupera aos poucos.

### Passo 6 — Rodar a suíte de testes

```bash
./mvnw test
```

Deve continuar em 225/225 — como o filtro só age em `/api/auth/login`, e os testes existentes de `AuthControllerTest` usam `@WebMvcTest`/mocks que não passam pela cadeia real de filtros do Spring Security, não deve haver impacto. Se algum teste de integração completo (`@SpringBootTest`) fizer múltiplas chamadas de login em sequência rápida, pode ser necessário resetar o estado do `LoginRateLimitFilter` entre testes ou aumentar a capacidade só no profile de teste — validar durante a implementação.

### Passo 7 — Deploy

Nenhuma variável de ambiente nova é necessária. Nenhuma ação além do deploy normal.

---

## 7. Riscos e mitigações

| Risco | Mitigação |
|---|---|
| Estado em memória some a cada restart/deploy | Aceitável — pior caso é o limite resetar, não é uma falha de segurança grave, só uma janela curta sem proteção |
| Múltiplas instâncias dobram o limite efetivo | Não é o cenário atual (uma instância). Documentado como follow-up para quando houver necessidade de escalar horizontalmente |
| `RemoteAddr` não reflete IP real atrás de proxy | Documentado como limitação conhecida; revisitar ao definir a infra de staging/produção |
| Memória crescendo indefinidamente (`ConcurrentHashMap` por IP nunca é limpo) | Baixo risco no volume atual — cada entrada é minúscula (um bucket com poucos bytes de estado). Se virar problema real, dá pra adicionar expiração depois (não faz sentido resolver preventivamente algo que ainda não é um problema) |
