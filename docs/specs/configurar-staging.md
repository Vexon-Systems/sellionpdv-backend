# Spec: Ambiente de Staging

> **Status**: Concluído e validado em 2026-07-09 — staging no ar em `https://sellionpdv-backend-1.onrender.com`, Flyway criou o schema do zero (V1+V2), login retorna accessToken/refreshToken corretamente.
>
> **Achado real durante a validação**: o primeiro deploy falhou com `relation "usuarios" does not exist` — não por bug de código, mas porque a branch `dev` (que o staging rastreia) estava 8 commits atrás de `feat/refatoracao-comercial`, sem nenhum Flyway configurado ainda. Resolvido fazendo merge da branch de feature pra `dev` antes do deploy funcionar. Lição: ao criar um ambiente novo que rastreia uma branch de longa duração, confirmar que ela está atualizada com o trabalho recente antes de investigar bug de código.
> **Esforço estimado**: ~2-3 horas (setup) + validação — a maior parte é ação manual sua no painel do Render/Supabase, não código
> **Prioridade**: Alta (Tier 1 — hoje toda mudança vai direto pra produção, sem lugar seguro pra testar antes)

---

## 1. Resumo

Hoje não existe staging: a única forma de testar uma mudança "de verdade" (contra Postgres real, com o Docker de produção) é publicando direto em produção. Isso é o oposto do que se quer num sistema com clientes de verdade.

**Achado importante ao investigar**: o repositório não tem nenhum arquivo de configuração de deploy (`render.yaml`, `railway.json`, etc.) — o Render permite configurar tudo pelo painel deles, sem exigir arquivo no repo. Confirmei que produção **já está rodando lá**, configurada manualmente. Isso muda o formato desta spec: a maior parte do trabalho é ação sua no painel do Render e do Supabase, não código. Minha parte é:
1. Ajustar 2 arquivos pra tornar a mesma imagem Docker reutilizável tanto pra staging quanto produção (hoje ela está travada em produção — ver seção 3).
2. Te dar o passo a passo exato do que configurar nos painéis.

**Desenho**: um segundo Web Service no Render, apontando pra branch `dev` (que já existe no seu repo) em vez de `main`, com deploy automático a cada push — e um segundo projeto Supabase, isolado do banco de produção. Mesmo Dockerfile, mesmo código, dados diferentes.

---

## 2. Por que a branch `dev` e não uma branch nova

Seu repositório já tem `main` e `dev` como branches de longa duração (confirmei via `git branch`). O CI (`ci-backend.yml`) já roda testes em push pra ambas. Faz sentido que:
- `dev` → auto-deploy pra **staging**
- `main` → auto-deploy pra **produção** (fluxo que já existe hoje, sem mudança)

Fluxo de trabalho resultante: feature → merge em `dev` → sobe sozinho pro staging → testa lá → merge em `main` → sobe sozinho pra produção. Nenhuma branch nova pra criar ou lembrar de usar.

---

## 3. Arquivos exatos criados/modificados

| Arquivo | Ação | O que muda |
|---|---|---|
| `Dockerfile` | Modificar | Remove `--spring.profiles.active=prod` fixo no `ENTRYPOINT` |
| `src/main/resources/application-staging.properties` | **Criar** | Espelha `application-prod.properties` (mesmas otimizações de log) |

### Por que mexer no Dockerfile

Hoje o `ENTRYPOINT` força `--spring.profiles.active=prod` **dentro da imagem**:

```dockerfile
ENTRYPOINT ["java", ..., "-jar", "app.jar", "--spring.profiles.active=prod"]
```

Isso significa que a mesma imagem Docker **nunca poderia rodar como staging** — ela sempre ativa o profile `prod`, não importa onde for implantada. Pra reusar a mesma imagem nos dois ambientes (que é o objetivo: "mesmo Dockerfile, dados diferentes"), o profile ativo precisa vir de **fora** da imagem, via variável de ambiente `SPRING_PROFILES_ACTIVE` — que o Spring Boot já lê nativamente, sem nenhum código novo.

```dockerfile
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
```

**Ação crítica que você precisa lembrar de fazer**: como o `--spring.profiles.active=prod` sai da imagem, o serviço de **produção que já existe hoje** no Render precisa ganhar a variável de ambiente `SPRING_PROFILES_ACTIVE=prod` — senão, no primeiro deploy depois dessa mudança, produção deixa de ativar o profile `prod` (volta pro comportamento default, com `spring.jpa.show-sql=true`, por exemplo — não é grave, mas é um comportamento não-intencional). Isso está detalhado como Passo 1 da seção 5, **antes** de mexer em qualquer outra coisa.

---

## 4. O que só você pode fazer (fora deste repositório)

### 4.1 — Segundo projeto Supabase (staging)

1. Criar um novo projeto Supabase, ex.: `sellionpdv-staging` (free tier).
2. Criar um bucket `produtos-imagens-staging` (público), igual foi feito pro de dev.
3. Anotar: connection string (Session pooler, não Transaction), URL do projeto, `service_role`/secret key.

**Por que um projeto novo e não reusar o de dev**: se staging e dev compartilhassem banco, um teste malfeito em staging (ex.: testar exclusão em massa) contaminaria os dados que você usa no dia a dia pra desenvolver. Separado custa zero a mais (Supabase free tier) e elimina esse risco.

### 4.2 — Segundo Web Service no Render

1. No painel do Render, criar um novo **Web Service**, mesmo repositório Git, branch `dev`, runtime Docker (mesmo `Dockerfile`).
2. Configurar as variáveis de ambiente (seção 4.3).
3. Confirmar que "Auto-Deploy" está ativado pra branch `dev`.

### 4.3 — Variáveis de ambiente do serviço de staging

| Variável | Valor |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `staging` |
| `DB_URL` | Connection string do Supabase de staging (novo projeto) |
| `DB_USERNAME` | Idem |
| `DB_PASSWORD` | Idem |
| `JWT_SECRET` | **Um valor novo e diferente do de produção** — gerar uma string aleatória de 32+ caracteres. Segredos diferentes por ambiente evitam que um token de staging funcione em produção ou vice-versa |
| `SENTRY_DSN` | Mesma DSN do projeto Sentry já criado |
| `SENTRY_ENVIRONMENT` | `staging` — o Sentry já suporta separar eventos por ambiente na mesma conta, sem precisar de projeto novo |
| `SUPABASE_URL` | URL do novo projeto Supabase de staging |
| `SUPABASE_SERVICE_ROLE_KEY` | Secret key do novo projeto |
| `SUPABASE_STORAGE_BUCKET` | `produtos-imagens-staging` |
| `CORS_ALLOWED_ORIGINS` | URL do frontend de staging, quando existir; até lá, pode deixar o default ou apontar pro seu `localhost` de desenvolvimento |

### 4.4 — Ação no serviço de produção existente (não é opcional, ver seção 3)

Adicionar `SPRING_PROFILES_ACTIVE=prod` nas variáveis de ambiente do Web Service de produção **já existente** no Render, antes ou junto do próximo deploy.

---

## 5. Passo a passo

### Passo 1 — Preparar produção antes de tocar no Dockerfile

No painel do Render, no serviço de **produção**, adicionar a variável `SPRING_PROFILES_ACTIVE=prod`. Isso pode ser feito a qualquer momento antes do próximo deploy de produção — não precisa esperar essa spec inteira ser implementada, só precisa acontecer **antes** de produção rodar a imagem sem o flag fixo.

### Passo 2 — Validar a troca de profile localmente (sem Docker)

Como não há Docker instalado nesta máquina, valido o mecanismo de seleção de profile diretamente com o Spring Boot (é o mesmo código, só sem o empacotamento Docker):

```bash
SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run
```

Confirmar no log de boot a linha `The following 1 profile is active: "prod"` (ou equivalente). Repetir com `SPRING_PROFILES_ACTIVE=staging` depois que `application-staging.properties` existir, confirmando que o Spring reconhece o profile `staging` sem erro.

### Passo 3 — Aplicar as mudanças no Dockerfile e criar `application-staging.properties`

Implementação em si (seção 3).

### Passo 4 — Criar o projeto Supabase de staging e o Web Service no Render

Ações manuais da seção 4. **Aqui eu não posso agir por você** — são contas/paineis que só você acessa.

### Passo 5 — Confirmar o primeiro deploy do staging

Depois do Render terminar o build (alguns minutos), acessar a URL pública do serviço de staging e verificar:
- `GET /swagger-ui.html` carrega.
- Nos logs do Render, o Flyway aplica **as duas migrations do zero** (`V1` e `V2`) — é a primeira vez que alguém roda o conjunto completo de migrations num banco novo desde que existem duas.
- Um `POST /api/auth/login` contra o staging funciona (vai precisar criar um usuário de teste lá — o banco está vazio, sem os dados de dev).

### Passo 6 — Confirmar que produção não quebrou

Depois do deploy de staging, verificar produção continua respondendo normalmente e que o log de boot mostra `profile: prod` (não profile nenhum). Se produção não foi redeployada nesse processo, essa checagem só é necessária no próximo deploy dela.

### Passo 7 — Rodar a suíte de testes

```bash
./mvnw test
```

Não deve haver nenhum impacto — mudança é só de infraestrutura/config, sem código de aplicação alterado.

---

## 6. Riscos e mitigações

| Risco | Mitigação |
|---|---|
| Produção perder o profile `prod` no deploy seguinte (esquecer o Passo 1) | Destacado como ação crítica logo no início da spec e como primeiro passo do plano de execução |
| Custo: dois serviços Free no Render compartilham a cota de 750h/mês por workspace | Staging não recebe tráfego constante (só uso interno), consome pouco da cota. Se algum dia isso virar problema, é sinal de que staging está sendo usado o suficiente pra justificar um plano pago |
| Banco de staging ficar defasado do schema de produção | Não é um risco novo desta spec — é resolvido pelo Flyway (ADR 019): qualquer banco (dev, staging, prod) roda as mesmas migrations versionadas, então nunca ficam dessincronizados |
| Free tier do Render "dorme" após 15min de inatividade (cold start de ~1min ao acordar) | Aceitável e até desejável para staging (não é tráfego de cliente); relevante mencionar que, se produção também estiver em Free tier, esse mesmo comportamento já afeta clientes reais hoje — isso é uma decisão de plano de hospedagem, fora do escopo desta spec, mas vale seu conhecimento |
