### ADR 001: Estratégia de Isolamento Multi-Tenant e Soft Delete (Fundação)

**Data:** 01/04/2026

**Contexto:** Fase 1 (Segurança). Precisamos mapear as entidades base (`Tenant` e `Usuario`) garantindo o isolamento de dados e a não-exclusão física de registros (auditoria).

**Decisão:**
1. Adotar a anotação `@SQLRestriction("ativo = true")` do Hibernate 6 em nível de classe para todas as entidades que possuem a coluna `ativo`.
2. Para a entidade `Usuario`, **NÃO** utilizaremos a anotação nativa `@TenantId` do Hibernate. Faremos o mapeamento relacional padrão (`@ManyToOne`).

**Opções Descartadas:**
* Espalhar `where ativo = true` nas queries do Spring Data JPA (Descartado pelo alto risco de falha humana e vazamento de dados inativos).
* Usar `@TenantId` na entidade `Usuario` (Descartado porque, no momento do Login, o backend recebe apenas o e-mail e ainda não possui o Token JWT para informar ao Hibernate qual é o Tenant atual. Isso quebraria a query de autenticação).

**Trade-offs:**
* O uso do `@SQLRestriction` torna um pouco mais verboso buscar dados inativos (exige *native queries* ou bypass específico no EntityManager), mas priorizamos a segurança *Default Deny*.
* Não usar `@TenantId` no `Usuario` significa que o isolamento desta entidade específica dependerá da lógica do Service/Repository, mas destrava o fluxo de Login perfeitamente. (As demais entidades operacionais como Vendas e Caixas usarão a barreira automática do Hibernate).

---

### ADR 002: Padrão de Repositories e Consultas

**Data:** 01/04/2026

**Contexto:** Precisamos definir como os dados serão acessados.

**Decisão:** Utilizar Spring Data JPA com foco em Query Methods e @Query (JPQL) apenas quando necessário para otimização (ex: Fetch Joins).

**Opções Descartadas:** 
* JDBC puro (Muito verboso para o estágio atual).
* QueryDSL (Adiciona complexidade de compilação desnecessária para o MVP).

**Trade-offs:** 
* O Spring Data facilita a escrita, mas exige cuidado com o problema de N+1. Resolveremos isso usando `JOIN FETCH` em consultas específicas de listagem.

---

### ADR 003: Estrutura de Diretórios - Package-by-Feature

**Data:** 01/04/2026

**Contexto:** À medida que o sistema cresce, o padrão de camadas (Package-by-Layer) dispersa a lógica de negócio de um mesmo domínio em várias pastas diferentes, dificultando a navegação.

**Decisão:** Adotar a arquitetura **Package-by-Feature** (Módulos por Funcionalidade). Cada domínio do negócio (Produto, Venda, Caixa, Usuário, Tenant) terá seu próprio pacote contendo Controller, Service, Repository, DTOs e a própria Entidade.

**Exceções:** Classes transversais irão para pacotes técnicos como `security`, `config`, `common` e `exception`.

**Trade-offs:** 
* **Vantagem:** Alta coesão. É muito mais fácil para um desenvolvedor novo entender onde está o código de "Vendas".
* **Desvantagem:** Exige disciplina para evitar acoplamento excessivo entre as features (ex: `VendaService` chamando diretamente o `ProdutoRepository` em vez do `ProdutoService`, embora aceitável em cenários de leitura simples, deve ser monitorado).

---

### ADR 004: Configuração Base de Segurança e Hash de Senhas

**Data:** 01/04/2026

**Contexto:** Necessidade de inicializar o Spring Security, liberar a rota de Login e definir o algoritmo de criptografia de senhas.

**Decisão:** 1. Adotar `Argon2PasswordEncoder` do Spring Security como padrão inviolável de hash.

2. Configurar o `SecurityFilterChain` como `STATELESS` (Sem sessão de servidor), pois o estado será mantido pelo Token JWT no frontend.
3. Desativar proteção CSRF (seguro e padrão para APIs REST baseadas em JWT).
   **Trade-offs:** O Argon2 consome mais memória e CPU (intencionalmente, para evitar ataques de força bruta) em comparação ao BCrypt.
   **Dependência Extra:** O Argon2 requer a biblioteca Bouncy Castle no `pom.xml` ou `build.gradle` (ex: `org.bouncycastle:bcprov-jdk18on`).

---

### ADR 005: Estratégia de Geração e Assinatura de Tokens (JWT)

**Data:** 02/04/2026

**Contexto:** Após validar as credenciais, o sistema precisa emitir um token seguro e sem estado (stateless) para identificar o usuário e seu Tenant nas requisições subsequentes.

**Decisão:** Utilizar a biblioteca `java-jwt` da Auth0. O Token conterá o e-mail no "subject" (padrão) e, crucialmente, o `tenantId` e `usuarioId` como "Custom Claims" (Cargas úteis customizadas). O algoritmo de assinatura será HMAC256.

**Trade-offs:** 
* **Vantagem:** O Backend não precisa fazer uma query extra no banco de dados só para saber de qual Tenant o usuário é; a informação viaja segura dentro do Token.
* **Atenção:** O Token não é criptografado, apenas *assinado*. Qualquer um pode ler o conteúdo dele, mas só o nosso Backend consegue verificar se ele é válido e se não foi adulterado. (Por isso nunca colocamos senhas dentro do JWT).

---

### ADR 006: Intercepção de Requisições e Contexto de Segurança

**Data:** 02/04/2026

**Contexto:** Toda requisição para rotas protegidas deve ter seu Token JWT validado. Além disso, precisamos extrair o `tenantId` para garantir o isolamento de dados.

**Decisão:** Criar um `SecurityFilter` estendendo `OncePerRequestFilter`.

1. O filtro extrai o token do cabeçalho `Authorization`.
2. Valida a assinatura via `TokenService`.
3. Recupera o usuário do banco de dados.
4. Injeta o usuário e suas permissões no `SecurityContextHolder` do Spring.

**Trade-offs:** 
- **Vantagem:** Centraliza a segurança. Os Controllers não precisam se preocupar se o usuário está logado ou a qual Tenant ele pertence.
- **Custo:** Cada requisição protegida fará uma consulta ao banco para validar o usuário. (Poderíamos usar cache no futuro, mas para o MVP, a consistência é prioridade).

---

### ADR 007: Gestão de Segredos e Variáveis de Ambiente

**Data:** 03/04/2026

**Contexto:** Evitar o vazamento de credenciais (Banco de Dados, Chaves JWT, APIs de pagamento) no controle de versão (GitHub).

**Decisão:** 

1. Adotar a injeção de dependência via Variáveis de Ambiente (`${NOME_DA_VARIAVEL}`).
2. Utilizar a biblioteca `spring-dotenv` para carregar automaticamente um arquivo `.env` raiz durante o desenvolvimento local.
3. Manter o `application.properties` versionado no Git apenas como um "mapa" estrutural, sem nenhum valor sensível real.
   
**Trade-offs:** Adiciona uma pequena dependência no projeto, mas zera o risco de vazamento acidental e facilita o deploy (na nuvem, basta cadastrar essas mesmas variáveis no painel da AWS, Heroku, etc).

---

### ADR 008: Isolamento Multi-Tenant Automático 

**Data:** 03/04/2026

**Contexto:** Garantir que o Backend NUNCA vaze dados de um Tenant para outro, e que os desenvolvedores não precisem lembrar de escrever `WHERE tenant_id = X` em todas as consultas SQL do sistema.

**Decisão:** Utilizar a interface `CurrentTenantIdentifierResolver` do Hibernate 6.
1. O `SecurityFilter` extrai o `tenant_id` do usuário logado e guarda em uma variável isolada por requisição (`ThreadLocal`).
2. O Hibernate, antes de rodar qualquer query nas entidades com `@TenantId`, pergunta para o nosso `Resolver` qual é o ID atual e injeta a trava do banco automaticamente.
   
**Trade-offs:** Torna o fluxo um pouco "mágico" (escondido), o que pode confundir devs juniores debugando o SQL, mas reduz o erro humano de vazamento de dados a praticamente zero.

---

### ADR 009: Mapeamento de Entidades Operacionais com @TenantId

**Data:** 03/04/2026

**Contexto:** Início da Fase 2. Precisamos garantir que todas as entidades de negócio (Categorias, Produtos, Vendas, etc.) respeitem o isolamento Multi-Tenant configurado na Fase 1.

**Decisão:** Utilizar a anotação `@TenantId` (nativa do Hibernate 6) na coluna `tenant_id` de TODAS as entidades operacionais.

**Trade-offs:** 
* **Vantagem:** Com o `TenantIdentifierResolver` rodando nos bastidores, o simples ato de colocar essa anotação no atributo da classe faz com que o Hibernate injete `tenant_id = X` em TODOS os `SELECT`, `INSERT`, `UPDATE` e `DELETE` daquela entidade. O desenvolvedor não precisa (e nem deve) passar o tenantId manualmente via DTO ou Service.

---

### ADR 010: Gestão Nativa de Segredos via Properties

**Data:** 03/04/2026

**Contexto:** Bibliotecas de `.env` de terceiros e imports de arquivos fora do classpath causam instabilidade no ambiente de desenvolvimento local dependendo da IDE utilizada.

**Decisão:** Abandonar o arquivo `.env` solto na raiz e utilizar a importação nativa do Spring Boot via Classpath com um arquivo de propriedades exclusivo para segredos.

1. Criaremos um arquivo `application-secret.properties` dentro de `src/main/resources`.
2. O `application.properties` principal importará este arquivo nativamente.
3. O `application-secret.properties` já está protegido pelo nosso `.gitignore` criado anteriormente, garantindo zero vazamentos no GitHub.
   
**Trade-offs:** Deixa de usar a extensão exata `.env`, mas ganha 100% de compatibilidade com qualquer IDE sem configuração extra. 
Em produção, as variáveis de ambiente do servidor continuarão sobrescrevendo esses valores normalmente.

---

### ADR 011: Estratégia de Testes Unitários

**Data:** 04/04/2026

**Contexto:** Garantir a qualidade e a inviolabilidade das regras de negócio sem desacelerar a entrega do MVP.

**Decisão:** 
1. Focar os testes unitários nas classes da camada **Service** (onde mora o lucro e a segurança do sistema).
2. Utilizar **JUnit 5** como motor de testes e **Mockito** para simular (mockar) o Banco de Dados.
3. Não testar Controllers ou Repositories triviais neste primeiro momento (focaremos no comportamento da regra de negócio, isolado do framework web).
   
**Trade-offs:** Testes puramente unitários não garantem que a requisição HTTP inteira funciona (para isso servem os testes de integração), mas rodam em milissegundos e blindam a lógica matemática e as validações (ex: Zero Trust) de forma extremamente barata e rápida.

---

### ADR 012: Modelagem do Domínio de Modificadores (Agregação)

**Data:** 04/04/2026

**Contexto:** Precisamos mapear as tabelas `grupos_modificadores` e `opcoes_modificadores`.

**Decisão:** 
1. Adotar o padrão de Raiz de Agregação (Aggregate Root). O `GrupoModificador` gerenciará a lista de `OpcaoModificador` através de um relacionamento `@OneToMany` com `CascadeType.ALL` e `orphanRemoval = true`.
2. A entidade `OpcaoModificador` receberá o filtro de Soft Delete (`@SQLRestriction`), pois possui a coluna `ativo` no script SQL. A entidade `GrupoModificador` **não** receberá, pois o script original não previu essa coluna para ela.
3. Ambas recebem o `@TenantId` inviolável.
   
**Trade-offs:** Centralizar o salvamento no Grupo facilita a transação (salvamos o grupo e as opções de uma vez só), mas exige cuidado no Frontend para enviar o JSON (Payload) completo ao criar ou editar um grupo.

---

### ADR 013: DTOs Aninhados e Persistência de Agregados

**Data:** 04/04/2026

**Contexto:** Grupos e Opções de Modificadores são interdependentes. Criar uma opção sem um grupo é impossível no nosso domínio.

**Decisão:** 
1. Utilizar DTOs aninhados (uma lista de `OpcaoRequestDTO` dentro do `GrupoRequestDTO`).
2. O Service será responsável por converter esse grafo de DTOs para o grafo de Entidades, utilizando os métodos de sincronização (`adicionarOpcao`) para garantir que o `grupo_id` seja preenchido corretamente em cada opção antes de salvar.
   
*Trade-offs:** O payload do POST fica ligeiramente maior, mas reduzimos o número de pedidos ao servidor (em vez de 1 pedido para o grupo e 5 para as opções, fazemos apenas 1 pedido atómico).

---

### ADR 014: Modelagem da Entidade Produto e Relacionamentos (REVISADA)
**Data:** 13/04/2026

**Contexto:** O Produto é a entidade central do Catálogo. O script do Supabase revelou que a tabela de ligação `produto_grupos_modificadores` não é simples; ela possui regras de negócio cruciais (`tipo_escolha`, `min_opcoes`, `max_opcoes`).

**Decisão:** 
1. Abandonamos o `@ManyToMany` simples.
2. Criamos uma **Entidade Intermediária** chamada `ProdutoGrupoModificador` com uma chave composta (`@EmbeddedId`).
3. O relacionamento passa a ser `@OneToMany` do Produto para a entidade intermediária, e `@ManyToOne` da intermediária para o Grupo.
4. Mantemos o `@SQLRestriction("ativo = true")` em todas as tabelas do catálogo (Produtos, Categorias, Modificadores) para garantir o Soft Delete.

---

### ADR 015: Stack de Documentação da API (OpenAPI)
**Data:** 13/04/2026

**Contexto:** Precisamos gerar a documentação visual da API e permitir testes via interface, mas o projeto utiliza Spring Boot 3/4, onde bibliotecas antigas como o SpringFox (Swagger 2) deixaram de funcionar.

**Decisão:**
1. Adotamos o **SpringDoc OpenAPI 3.x** (`springdoc-openapi-starter-webmvc-ui`).
2. A documentação ficará acessível em `/swagger-ui/index.html`.
3. Configuramos o `OpenApiConfig` para injetar globalmente o esquema de segurança (Bearer Token / JWT), permitindo que o Frontend ou QA teste as rotas de Multi-Tenant diretamente pelo navegador sem tomar erro 403.
4. As rotas do Swagger foram adicionadas à *whitelist* do `SecurityConfig`.

---

### ADR 016: Padronização da Estratégia de Deleção (Soft Delete)
**Data:** 13/04/2026

**Contexto:** Ao inativar categorias e modificadores, precisamos decidir entre o uso de booleanos (`ativo`) ou carimbos de tempo (`deleted_at`).

**Decisão:**
1. Escolhemos a abordagem **Boolean (`ativo = true/false`)**.
2. **Motivo:** O script SQL original do Supabase já utilizava este padrão nas tabelas `produtos` e `tenants`. A decisão mantém a consistência arquitetural do banco.
3. Executamos um script SQL de `ALTER TABLE` para injetar a coluna `ativo BOOLEAN NOT NULL DEFAULT true` nas tabelas `categorias` e `grupos_modificadores`, padronizando todo o Módulo de Catálogo.

---

### ADR 017: Estrutura do Payload de Catálogo (Árvore JSON)
**Data:** 13/04/2026

**Contexto:** O Frontend do PDV precisa montar o ecrã de vendas de forma extremamente rápida. Fazer dezenas de chamadas HTTP (uma para o produto, outra para os grupos, outra para as opções) geraria latência no momento da venda.

**Decisão:**
1. A rota `GET /api/produtos` retornará uma **Árvore JSON Profunda (Nested)**.
2. O DTO de resposta do Produto engloba a lista de Grupos, que por sua vez engloba a lista de Opções Ativas.
3. **Trade-off aceito:** O payload do `GET` será maior em KBs, mas permite que o Frontend faça o cache completo do catálogo na memória (RAM) no início do turno, garantindo zero latência na navegação do utilizador durante as vendas.

---

### ADR 018: Estratégia de Sincronização JPA (Evitando Conflito de Persistência)
**Data:** 13/04/2026

**Contexto:** Ao atualizar os modificadores de um Produto (`PUT`), o uso de `.clear()` na lista de relacionamentos com chave composta gerava o erro de "Conflito de Contexto de Persistência" (o Hibernate tentava deletar e recriar a mesma chave composta na mesma transação).

**Decisão:**
1. Abandonamos a estratégia de "Destruir e Recriar" (`.clear()`).
2. Adotamos o **Motor de Sincronização Inteligente (Merge)** no `ProdutoService`.
3. O código agora compara ativamente o que veio no JSON contra o que está no Banco de Dados e decide granularmente se deve: Remover (o que sumiu do JSON), Atualizar (o que já existia) ou Adicionar (o que é novo).
4. Essa abordagem evita erros 500 silenciosos do JPA e otimiza a quantidade de queries geradas no banco.

---

### ADR 019: Versionamento de Schema com Flyway (Baseline)
**Data:** 08/07/2026

**Contexto:** Até aqui, `spring.jpa.hibernate.ddl-auto=none` e todas as mudanças de schema eram aplicadas manualmente via SQL avulso no Supabase (ver ADR 016). Sem registro automático de qual versão do schema está em cada ambiente, um `ALTER TABLE` esquecido em produção quebra a aplicação silenciosamente. Ver spec completa em `docs/specs/configurar-flyway.md`.

**Decisão:**
1. Adotamos o **Flyway** para versionar o schema, com os arquivos de migration em `src/main/resources/db/migration/`.
2. Como o banco de dev/prod já existia com tabelas criadas manualmente, usamos a estratégia de **baseline**: `V1__baseline_schema.sql` contém o DDL completo extraído do banco real (via `pg_dump`), e `spring.flyway.baseline-on-migrate=true` + `spring.flyway.baseline-version=1` fazem o Flyway reconhecer esse schema como ponto de partida sem tentar recriá-lo. Em um banco novo e vazio (ex.: futuro staging), o mesmo `V1` cria o schema do zero.
3. **Dependência correta no Spring Boot 4:** `org.springframework.boot:spring-boot-starter-flyway` (não `flyway-core` isolado). O Boot 4 modularizou a autoconfiguração por feature (assim como fez com Thymeleaf); sem o starter dedicado, o Flyway fica no classpath mas nunca é acionado — nenhum log, nenhum erro, apenas silêncio. Descoberto via [flyway/flyway#4165](https://github.com/flyway/flyway/issues/4165).
4. Nos testes (H2 + `create-drop`), o Flyway é desabilitado (`spring.flyway.enabled=false`) — não faz sentido versionar schema num banco recriado do zero a cada suíte.

**Opções Descartadas:**
* Liquibase (Descartado: Flyway com migrations SQL puras é mais simples de ler/manter para quem não é backend — XML/YAML do Liquibase adiciona uma camada de abstração desnecessária).
* Continuar com ALTER TABLE manual (Descartado: é exatamente o risco que motivou essa mudança).

**Trade-offs:**
* A partir de agora, **nenhuma mudança de schema pode ser feita manualmente no Supabase** — toda alteração vira um novo arquivo `V{n}__descricao.sql`, versionado e revisado como qualquer código.
* O baseline não valida retroativamente se o `V1` bate 100% com o schema real; isso foi validado manualmente uma vez (criação do zero num banco descartável + baseline em dev), mas divergências futuras só aparecerão se alguém tentar recriar o banco do zero.

---

### ADR 020: Observabilidade de Erros com Sentry
**Data:** 08/07/2026

**Contexto:** Não existia nenhuma ferramenta de observabilidade — um erro em produção só era percebido quando o cliente reclamava. Ver spec completa em `docs/specs/configurar-sentry.md`.

**Decisão:**
1. Adotamos o **Sentry** para captura automática de exceções, com alerta quando algo novo quebra.
2. Como o `GlobalExceptionHandler` (`@RestControllerAdvice`) já intercepta todas as exceções antes de qualquer mecanismo automático do Spring, a integração automática do Sentry não dispara sozinha — foi necessário adicionar `Sentry.captureException(ex)` explicitamente, e **só** dentro do handler de erro 500 genuinamente inesperado (`handleGeneric`). Os outros 5 handlers (404, 422, 400, header ausente, JSON malformado) não chamam o Sentry — são fluxo normal da aplicação, não bugs, e mandar isso pro Sentry geraria ruído.
3. **Dependência correta no Spring Boot 4:** `io.sentry:sentry-spring-boot-4` (não `sentry-spring-boot-starter-jakarta`, que é a variante para Boot 3). A `-jakarta` resolve sem erro no Maven, mas falha no boot com `"Incompatible Spring Boot Version detected!"` — o mesmo padrão de armadilha do Flyway (ADR 019): dependências de terceiros ainda estão migrando suporte pro Boot 4, e "resolveu no Maven" não significa "funciona em runtime".
4. `sentry.dsn=${SENTRY_DSN:}` fica vazia por padrão — o SDK se desliga sozinho sem DSN configurada, então dev/teste/CI funcionam sem nenhuma configuração extra.

**Opções Descartadas:**
* Deixar a integração automática do Sentry sem chamada explícita (Descartado: não funcionaria, porque o `GlobalExceptionHandler` já "trata" toda exceção do ponto de vista do Spring).
* Mandar todos os erros (incluindo 4xx) pro Sentry (Descartado: geraria ruído e faria a equipe ignorar alertas reais).

**Trade-offs:**
* Qualquer novo `@ExceptionHandler` que represente um erro genuinamente inesperado (não um fluxo de negócio) precisa lembrar de chamar `Sentry.captureException` manualmente — não é automático.
* Validado com um evento de teste manual disparado via SDK diretamente (sem passar pela API HTTP); o caminho HTTP real (`GlobalExceptionHandler` → Sentry) foi validado por inspeção de código, não por chamada end-to-end.

---

### ADR 021: Armazenamento de Imagens com Supabase Storage
**Data:** 08/07/2026

**Contexto:** `ProdutoService.uploadImagem()` e `UsuarioService.uploadAvatar()` salvavam arquivo em disco local (`uploads/`), servido via `WebConfig`. Sem volume persistente configurado, toda imagem desaparecia a cada deploy — o próprio Dockerfile já alertava sobre isso em comentário. Ver spec completa em `docs/specs/configurar-supabase-storage.md`.

**Decisão:**
1. Adotamos o **Supabase Storage** (bucket público `produtos-imagens`, mesmo projeto que já hospeda o banco) no lugar do disco local.
2. Extraída uma interface `ImagemStorage` (`common/storage/`) com implementação `SupabaseImagemStorage`, injetada tanto em `ProdutoService` quanto em `UsuarioService` — os dois tinham o mesmo padrão de upload em disco, descoberto durante a implementação (não estava no escopo original da spec, estendido com aprovação).
3. `SupabaseImagemStorage` usa `RestClient` (já disponível via `spring-boot-starter-web`, sem dependência nova) fazendo `PUT /storage/v1/object/{bucket}/{arquivo}` com a `service_role`/`secret key` do Supabase nos headers `Authorization: Bearer` e `apikey`.
4. `WebConfig` (só existia para servir `/uploads/**`) foi removida por completo.

**Opções Descartadas:**
* Manter volume Docker persistente pro `uploads/` local (Descartado: acopla a aplicação a um único host/volume, não funciona bem com múltiplas instâncias ou plataformas PaaS sem storage persistente nativo).

**Trade-offs:**
* Bug real de URI encontrado só ao testar com credenciais reais (não pelos testes unitários, que mockam `ImagemStorage`): `RestClient.uri("{url}/...", ...)` fazia URL-encode do placeholder, quebrando o esquema `https://`. Corrigido montando a URI via `String.formatted()`. Reforça que "resolveu a dependência" e "compilou" não bastam — vale testar contra a API real antes do deploy.
* O Supabase reformulou o sistema de chaves durante essa implementação (`service_role` → `sb_secret_...`); testado e confirmado que o novo formato funciona nos mesmos headers.
* Bucket público: aceitável para imagens de produto/avatar (já eram públicas por design), mas esse padrão não deve ser reaproveitado sem revisão para conteúdo sensível no futuro.

---

### ADR 022: Rate Limit no Login com Bucket4j
**Data:** 09/07/2026

**Contexto:** `POST /api/auth/login` é `permitAll()` e aceitava chamadas ilimitadas — o Argon2id torna cada tentativa cara em CPU, mas não impede volume de tentativas de força bruta/credential stuffing. Ver spec completa em `docs/specs/configurar-rate-limit-login.md`.

**Decisão:**
1. Adotado **Bucket4j** (`bucket4j-core`, sem starter Spring, sem Redis) — algoritmo token bucket com estado em memória (`ConcurrentHashMap`), suficiente para uma instância só.
2. `LoginRateLimitFilter` (`security/`) intercepta só `POST /api/auth/login`, registrado na cadeia antes do `SecurityFilter`. Limite configurável via `application.properties` (`security.rate-limit.login.*`), default 5 tentativas/minuto por IP — sem variável de ambiente obrigatória.
3. Resposta de bloqueio usa o mesmo padrão `ProblemDetail` do `GlobalExceptionHandler`, mas construída manualmente no filtro (roda antes do Spring MVC, não passa pelo `@RestControllerAdvice`).

**Opções Descartadas:**
* Redis/estado compartilhado (Descartado: complexidade desproporcional para uma instância só; documentado como próximo passo se/quando escalar horizontalmente).
* Segunda `SecurityFilterChain` restrita por URL (Descartado: um `if` de path+método dentro do filtro é mais simples de ler que duas cadeias de segurança paralelas).

**Trade-offs:**
* Estado em memória reseta a cada deploy/restart e **não escala pra múltiplas instâncias** (cada uma teria seu próprio contador) — aceitável agora, revisitar se/quando houver mais de uma instância rodando.
* `request.getRemoteAddr()` não reflete o IP real atrás de proxy/load balancer (ex.: Railway, Render) — precisaria ler `X-Forwarded-For` com uma lista de proxies confiáveis. Não resolvido agora porque depende de qual infra de deploy for escolhida (Tier 1, item de staging/deploy, ainda em aberto).
* **Validado com curl em rajada, não descoberto na spec**: o `Refill.greedy` do Bucket4j repõe tokens continuamente ao longo da janela, não em janela fixa — testar com pausas manuais entre chamadas dá resultado enganoso (parece não bloquear). Só ficou claro testando em loop sem pausa.
* Tentativas bloqueadas retornam 422 antes do rate limit entrar em ação (não 401) — comportamento herdado do `GlobalExceptionHandler`, não alterado por esta mudança.

---

### ADR 023: Refresh Token com Rotação
**Data:** 09/07/2026

**Contexto:** O login gerava um único JWT de 2h, sem renovação (usuário precisava logar de novo ao expirar) e sem revogação (logout era só client-side; o token continuava válido no backend até expirar sozinho). Ver spec completa em `docs/specs/configurar-refresh-token.md`.

**Decisão:**
1. Par **access token (JWT, 15min) + refresh token (opaco, 30 dias)**. O access token continua stateless (validado só por assinatura, sem consulta ao banco); o refresh token é a única coisa nova persistida.
2. `RefreshToken` (tabela `refresh_tokens`, migration `V2` — primeira migration real desde o baseline do Flyway) guarda o **hash SHA-256** do token, nunca o valor bruto — mesma lógica de nunca guardar senha em texto puro. Sem `@TenantId`, pela mesma razão que `Usuario` não tem (ADR 001): o tenant não é conhecível antes de já ter localizado o token pelo hash.
3. **Rotação obrigatória**: cada uso de refresh token o revoga e emite um novo. Uso do token antigo depois disso falha — sinal de token comprometido, sem precisar de infraestrutura de detecção.
4. Novos endpoints `POST /api/auth/refresh` e `POST /api/auth/logout`, ambos `permitAll()` (não fazem sentido exigir um access token válido, já que o cenário típico de uso é justamente quando ele expirou).
5. `LoginResponseDTO.token` renomeado para `accessToken` + novo campo `refreshToken` — **quebra o contrato da API**, exige atualização coordenada do frontend (fora deste repositório).

**Opções Descartadas:**
* Blacklist de access tokens (Descartado: reintroduziria estado/consulta ao banco em toda requisição autenticada, exatamente o que o JWT stateless evita — o TTL curto de 15min já limita a janela de exposição sem isso).
* Refresh token também como JWT (Descartado: um valor opaco aleatório é mais simples de revogar — não precisa decodificar nada, só olhar o hash na tabela).

**Trade-offs:**
* Tabela `refresh_tokens` cresce a cada login/refresh, nunca é limpa automaticamente — aceitável no volume atual; job de limpeza fica como spec futura se necessário.
* Rate limit do login (ADR 022) não cobre `/api/auth/refresh` — o refresh token não é adivinhável por força bruta, risco bem menor que senha; documentado, não implementado.
* Validado ponta a ponta com curl contra o banco de dev real: login → refresh (tokens novos) → reuso do token antigo (falha, 422, confirma rotação) → logout (204) → refresh pós-logout (falha, 422).