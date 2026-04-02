## ADR 001: Estratégia de Isolamento Multi-Tenant e Soft Delete (Fundação)

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


## ADR 002: Padrão de Repositories e Consultas

**Data:** 01/04/2026

**Contexto:** Precisamos definir como os dados serão acessados.

**Decisão:** Utilizar Spring Data JPA com foco em Query Methods e @Query (JPQL) apenas quando necessário para otimização (ex: Fetch Joins).

**Opções Descartadas:** 
* JDBC puro (Muito verboso para o estágio atual).
* QueryDSL (Adiciona complexidade de compilação desnecessária para o MVP).

**Trade-offs:** 
* O Spring Data facilita a escrita, mas exige cuidado com o problema de N+1. Resolveremos isso usando `JOIN FETCH` em consultas específicas de listagem.


## ADR 003: Estrutura de Diretórios - Package-by-Feature

**Data:** 01/04/2026

**Contexto:** À medida que o sistema cresce, o padrão de camadas (Package-by-Layer) dispersa a lógica de negócio de um mesmo domínio em várias pastas diferentes, dificultando a navegação.

**Decisão:** Adotar a arquitetura **Package-by-Feature** (Módulos por Funcionalidade). Cada domínio do negócio (Produto, Venda, Caixa, Usuário, Tenant) terá seu próprio pacote contendo Controller, Service, Repository, DTOs e a própria Entidade.

**Exceções:** Classes transversais irão para pacotes técnicos como `security`, `config`, `common` e `exception`.

**Trade-offs:** 
* **Vantagem:** Alta coesão. É muito mais fácil para um desenvolvedor novo entender onde está o código de "Vendas".
* **Desvantagem:** Exige disciplina para evitar acoplamento excessivo entre as features (ex: `VendaService` chamando diretamente o `ProdutoRepository` em vez do `ProdutoService`, embora aceitável em cenários de leitura simples, deve ser monitorado).


## ADR 004: Configuração Base de Segurança e Hash de Senhas

**Data:** 01/04/2026

**Contexto:** Necessidade de inicializar o Spring Security, liberar a rota de Login e definir o algoritmo de criptografia de senhas.

**Decisão:** 1. Adotar `Argon2PasswordEncoder` do Spring Security como padrão inviolável de hash.

2. Configurar o `SecurityFilterChain` como `STATELESS` (Sem sessão de servidor), pois o estado será mantido pelo Token JWT no frontend.
3. Desativar proteção CSRF (seguro e padrão para APIs REST baseadas em JWT).
   **Trade-offs:** O Argon2 consome mais memória e CPU (intencionalmente, para evitar ataques de força bruta) em comparação ao BCrypt.
   **Dependência Extra:** O Argon2 requer a biblioteca Bouncy Castle no `pom.xml` ou `build.gradle` (ex: `org.bouncycastle:bcprov-jdk18on`).

## ADR 005: Estratégia de Geração e Assinatura de Tokens (JWT)

**Data:** 02/04/2026

**Contexto:** Após validar as credenciais, o sistema precisa emitir um token seguro e sem estado (stateless) para identificar o usuário e seu Tenant nas requisições subsequentes.

**Decisão:** Utilizar a biblioteca `java-jwt` da Auth0. O Token conterá o e-mail no "subject" (padrão) e, crucialmente, o `tenantId` e `usuarioId` como "Custom Claims" (Cargas úteis customizadas). O algoritmo de assinatura será HMAC256.

**Trade-offs:** 
* **Vantagem:** O Backend não precisa fazer uma query extra no banco de dados só para saber de qual Tenant o usuário é; a informação viaja segura dentro do Token.
* **Atenção:** O Token não é criptografado, apenas *assinado*. Qualquer um pode ler o conteúdo dele, mas só o nosso Backend consegue verificar se ele é válido e se não foi adulterado. (Por isso nunca colocamos senhas dentro do JWT).

## ADR 006: Intercepção de Requisições e Contexto de Segurança

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