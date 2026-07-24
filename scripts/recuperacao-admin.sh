#!/bin/sh
# Execução guiada da SEL-SEC-028. Use somente em Shell efêmero autorizado.
set -eu

printf '%s\n' 'Recuperação administrativa — produção'
printf '%s\n' 'Use somente com aprovação registrada. A senha não será exibida nem registrada.'

printf 'Tenant ID: '
IFS= read -r ADMIN_RECOVERY_TENANT_ID
case "$ADMIN_RECOVERY_TENANT_ID" in
  ''|*[!0-9]*) printf '%s\n' 'Tenant ID deve ser um número inteiro positivo.' >&2; exit 2 ;;
esac

printf 'E-mail do administrador: '
IFS= read -r ADMIN_RECOVERY_EMAIL
case "$ADMIN_RECOVERY_EMAIL" in
  *@?*.*) ;;
  *) printf '%s\n' 'Informe um e-mail válido.' >&2; exit 2 ;;
esac

printf 'Identificador do operador: '
IFS= read -r ADMIN_RECOVERY_OPERATOR_ID
printf 'Identificador do ticket/aprovação: '
IFS= read -r ADMIN_RECOVERY_TICKET_ID
if [ -z "$ADMIN_RECOVERY_OPERATOR_ID" ] || [ -z "$ADMIN_RECOVERY_TICKET_ID" ]; then
  printf '%s\n' 'Operador e ticket são obrigatórios.' >&2
  exit 2
fi

trap 'stty echo 2>/dev/null || true' EXIT INT TERM
printf 'Senha temporária aleatória (mínimo 24 caracteres): '
stty -echo
IFS= read -r ADMIN_RECOVERY_TEMP_PASSWORD
stty echo
printf '\n'
if [ "${#ADMIN_RECOVERY_TEMP_PASSWORD}" -lt 24 ]; then
  printf '%s\n' 'A senha temporária deve ter ao menos 24 caracteres.' >&2
  exit 2
fi

export SPRING_PROFILES_ACTIVE='prod,admin-recovery'
export ADMIN_RECOVERY_ENABLED='true'
export ADMIN_RECOVERY_TENANT_ID ADMIN_RECOVERY_EMAIL ADMIN_RECOVERY_OPERATOR_ID
export ADMIN_RECOVERY_TICKET_ID ADMIN_RECOVERY_TEMP_PASSWORD

if java -XX:MaxRAMPercentage=75.0 \
  -Djava.security.egd=file:/dev/./urandom \
  -jar app.jar \
  --spring.main.web-application-type=none; then
  status=0
else
  status=$?
fi

unset ADMIN_RECOVERY_TEMP_PASSWORD
unset ADMIN_RECOVERY_TENANT_ID ADMIN_RECOVERY_EMAIL ADMIN_RECOVERY_OPERATOR_ID ADMIN_RECOVERY_TICKET_ID

if [ "$status" -eq 0 ]; then
  printf '%s\n' 'Concluído. Faça login pelo frontend com a senha temporária e altere-a imediatamente.'
fi
exit "$status"
