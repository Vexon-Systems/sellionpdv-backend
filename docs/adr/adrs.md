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

## ADR 007: Gestão de Segredos e Variáveis de Ambiente

**Data:** 03/04/2026

**Contexto:** Evitar o vazamento de credenciais (Banco de Dados, Chaves JWT, APIs de pagamento) no controle de versão (GitHub).

**Decisão:** 

1. Adotar a injeção de dependência via Variáveis de Ambiente (`${NOME_DA_VARIAVEL}`).
2. Utilizar a biblioteca `spring-dotenv` para carregar automaticamente um arquivo `.env` raiz durante o desenvolvimento local.
3. Manter o `application.properties` versionado no Git apenas como um "mapa" estrutural, sem nenhum valor sensível real.
   
**Trade-offs:** Adiciona uma pequena dependência no projeto, mas zera o risco de vazamento acidental e facilita o deploy (na nuvem, basta cadastrar essas mesmas variáveis no painel da AWS, Heroku, etc).

## ADR 008: Isolamento Multi-Tenant Automático 

**Data:** 03/04/2026

**Contexto:** Garantir que o Backend NUNCA vaze dados de um Tenant para outro, e que os desenvolvedores não precisem lembrar de escrever `WHERE tenant_id = X` em todas as consultas SQL do sistema.

**Decisão:** Utilizar a interface `CurrentTenantIdentifierResolver` do Hibernate 6.
1. O `SecurityFilter` extrai o `tenant_id` do usuário logado e guarda em uma variável isolada por requisição (`ThreadLocal`).
2. O Hibernate, antes de rodar qualquer query nas entidades com `@TenantId`, pergunta para o nosso `Resolver` qual é o ID atual e injeta a trava do banco automaticamente.
   
**Trade-offs:** Torna o fluxo um pouco "mágico" (escondido), o que pode confundir devs juniores debugando o SQL, mas reduz o erro humano de vazamento de dados a praticamente zero.

## ADR 009: Mapeamento de Entidades Operacionais com @TenantId

**Data:** 03/04/2026

**Contexto:** Início da Fase 2. Precisamos garantir que todas as entidades de negócio (Categorias, Produtos, Vendas, etc.) respeitem o isolamento Multi-Tenant configurado na Fase 1.

**Decisão:** Utilizar a anotação `@TenantId` (nativa do Hibernate 6) na coluna `tenant_id` de TODAS as entidades operacionais.

**Trade-offs:** 
* **Vantagem:** Com o `TenantIdentifierResolver` rodando nos bastidores, o simples ato de colocar essa anotação no atributo da classe faz com que o Hibernate injete `tenant_id = X` em TODOS os `SELECT`, `INSERT`, `UPDATE` e `DELETE` daquela entidade. O desenvolvedor não precisa (e nem deve) passar o tenantId manualmente via DTO ou Service.

## ADR 010: Gestão Nativa de Segredos via Properties

**Data:** 03/04/2026

**Contexto:** Bibliotecas de `.env` de terceiros e imports de arquivos fora do classpath causam instabilidade no ambiente de desenvolvimento local dependendo da IDE utilizada.

**Decisão:** Abandonar o arquivo `.env` solto na raiz e utilizar a importação nativa do Spring Boot via Classpath com um arquivo de propriedades exclusivo para segredos.

1. Criaremos um arquivo `application-secret.properties` dentro de `src/main/resources`.
2. O `application.properties` principal importará este arquivo nativamente.
3. O `application-secret.properties` já está protegido pelo nosso `.gitignore` criado anteriormente, garantindo zero vazamentos no GitHub.
   
**Trade-offs:** Deixa de usar a extensão exata `.env`, mas ganha 100% de compatibilidade com qualquer IDE sem configuração extra. 
Em produção, as variáveis de ambiente do servidor continuarão sobrescrevendo esses valores normalmente.