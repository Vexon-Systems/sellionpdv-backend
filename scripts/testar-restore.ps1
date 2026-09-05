# Requer PowerShell e Docker. Nunca recebe uma URL de banco remoto.
param(
    [Parameter(Mandatory)][string]$Dump,
    [Parameter(Mandatory)][string]$Responsavel,
    [Parameter(Mandatory)][string]$Relatorio
)
$ErrorActionPreference = 'Stop'
$dumpPath = (Resolve-Path -LiteralPath $Dump).Path
$checksumPath = $dumpPath + '.sha256'
$expected = ((Get-Content -LiteralPath $checksumPath -Raw).Trim() -split '\s+')[0]
$actual = (Get-FileHash -LiteralPath $dumpPath -Algorithm SHA256).Hash
if ($expected -notmatch '^[a-fA-F0-9]{64}$' -or $expected -ne $actual) {
    throw 'Checksum inválido. O restore não foi iniciado.'
}
if (Test-Path -LiteralPath $Relatorio) { throw 'Use um caminho novo para o relatório.' }
$containerName = 'sellion-restore-' + [Guid]::NewGuid().ToString('N')
$started = Get-Date
$created = $false
try {
    # Sem portas publicadas, sem rede e com volume temporário removido ao encerrar.
    docker run -d --rm --network none --name $containerName -e POSTGRES_PASSWORD=local-restore -e POSTGRES_DB=restore postgres:17-alpine | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Não foi possível criar o PostgreSQL descartável.' }
    $created = $true
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        docker exec $containerName pg_isready -U postgres -d restore 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'PostgreSQL não iniciou no prazo.' }
    docker cp $dumpPath "${containerName}:/tmp/backup.dump"
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao copiar o backup.' }
    docker exec $containerName pg_restore -U postgres -d restore --exit-on-error --no-owner --no-privileges /tmp/backup.dump
    if ($LASTEXITCODE -ne 0) { throw 'Restore falhou; não considerar o backup recuperável.' }
    docker cp (Join-Path $PSScriptRoot 'conferir-restore.sql') "${containerName}:/tmp/conferir.sql"
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao copiar consultas de conferência.' }
    $totals = docker exec $containerName psql -U postgres -d restore -v ON_ERROR_STOP=1 -f /tmp/conferir.sql
    if ($LASTEXITCODE -ne 0) { throw 'Falha nas consultas de conferência.' }
    @(
        "Responsável: $Responsavel"
        "Início UTC: $($started.ToUniversalTime().ToString('o'))"
        "Duração segundos: $([math]::Round(((Get-Date) - $started).TotalSeconds, 1))"
        "Backup: $(Split-Path $dumpPath -Leaf)"
        "SHA256: $actual"
        'Restore e consultas: OK. Reconciliação de negócio: PENDENTE de comparação com o fechamento da data do backup.'
        $totals
    ) | Set-Content -LiteralPath $Relatorio -Encoding utf8
    Write-Output "Restore concluído. Conferir totais no relatório privado: $Relatorio"
} finally {
    if ($created) {
        docker rm -f -v $containerName | Out-Null
        if ($LASTEXITCODE -ne 0) { Write-Warning "Remova o container descartável $containerName manualmente." }
    }
}
