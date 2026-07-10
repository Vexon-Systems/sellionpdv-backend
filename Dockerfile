# =============================================================
# Stage 1 — Build
# Usa a imagem oficial do Maven com JDK 21 para compilar o JAR.
# A suite de testes é ignorada (requer ajuste separado).
# =============================================================
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copia apenas o pom.xml primeiro para que o Docker cache as
# dependências em uma camada separada. Se o pom.xml não mudar,
# o "mvn dependency:go-offline" não é reexecutado.
COPY pom.xml ./
RUN mvn dependency:go-offline -B -q

# Copia o código-fonte e empacota
COPY src/ src/
RUN mvn package -Dmaven.test.skip=true -B -q


# =============================================================
# Stage 2 — Runtime
# Imagem mínima com apenas o JRE (sem ferramentas de build).
# =============================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

# Executa como usuário não-root por segurança
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

COPY --from=builder /app/target/sellionpdv-*.jar app.jar

USER spring

EXPOSE 8080

# -XX:MaxRAMPercentage=75.0   → limita o heap a 75% da memória do container
# -Djava.security.egd=...     → evita lentidão de startup por falta de entropia no Linux
#
# O profile ativo (prod, staging, etc.) NÃO é fixado aqui — vem da variável de
# ambiente SPRING_PROFILES_ACTIVE, configurada por serviço no provedor de deploy.
# Isso permite reusar a mesma imagem Docker em produção e staging. Ver
# docs/specs/configurar-staging.md.
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
