# Starts the local PostgreSQL (portable install in %LOCALAPPDATA%\StockFlowPostgres) if needed,
# then the API on port 8000 using it. Stop with Ctrl+C; PostgreSQL keeps running until
#   & "$env:LOCALAPPDATA\StockFlowPostgres\pgsql\bin\pg_ctl.exe" -D "$env:LOCALAPPDATA\StockFlowPostgres\data" stop
$pg = Join-Path $env:LOCALAPPDATA "StockFlowPostgres"
$pgCtl = Join-Path $pg "pgsql\bin\pg_ctl.exe"
$data = Join-Path $pg "data"

& $pgCtl -D $data status *> $null
if ($LASTEXITCODE -ne 0) {
    & $pgCtl -D $data -l (Join-Path $pg "server.log") -w start
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL could not start. See $pg\server.log" }
}

$env:DATABASE_URL = "postgresql+psycopg://stockflow@localhost:5432/stockflow"
& (Join-Path $PSScriptRoot ".venv\Scripts\python.exe") -m uvicorn app.main:app --port 8000 --app-dir $PSScriptRoot
