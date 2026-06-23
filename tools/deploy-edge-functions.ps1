# Deploy all hardened Supabase Edge Functions (requires: supabase login)
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)

$functions = @(
    @{ Name = "password-reset"; VerifyJwt = $false },
    @{ Name = "stripe-webhook"; VerifyJwt = $false },
    @{ Name = "get-deepgram-key"; VerifyJwt = $false },
    @{ Name = "admin-dashboard"; VerifyJwt = $true },
    @{ Name = "create-checkout"; VerifyJwt = $true },
    @{ Name = "customer-portal"; VerifyJwt = $true },
    @{ Name = "check-subscription"; VerifyJwt = $true },
    @{ Name = "ai-chat"; VerifyJwt = $true },
    @{ Name = "translate-text"; VerifyJwt = $true },
    @{ Name = "writing-tool"; VerifyJwt = $true },
    @{ Name = "complete-voice-workflow"; VerifyJwt = $true },
    @{ Name = "voice-to-text"; VerifyJwt = $true },
    @{ Name = "voice-conversion"; VerifyJwt = $true },
    @{ Name = "create-voice-clone"; VerifyJwt = $true },
    @{ Name = "free-translate-text"; VerifyJwt = $true },
    @{ Name = "free-voice-translate"; VerifyJwt = $true }
)

foreach ($fn in $functions) {
    Write-Host "Deploying $($fn.Name)..."
    if ($fn.VerifyJwt) {
        supabase functions deploy $fn.Name
    } else {
        supabase functions deploy $fn.Name --no-verify-jwt
    }
}

Write-Host "Done. Verify with: supabase functions list"
