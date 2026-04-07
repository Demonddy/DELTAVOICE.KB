# Copies app/src/main/res/values/strings.xml into any values-<qualifier> folder
# required by AppLocaleCatalog that does not yet have strings.xml.
# Hebrew (he): ensure values-he exists (mirrored from values-iw separately).
# Run from repo root: powershell -File tools/sync_locale_placeholders.ps1

$ErrorActionPreference = "Stop"
$resRoot = Join-Path $PSScriptRoot "..\app\src\main\res" | Resolve-Path
$defaultStrings = Join-Path $resRoot "values\strings.xml"

$pairs = @(
    @{ t = "ar"; s = "ar" },
    @{ t = "af"; s = "af" },
    @{ t = "sq"; s = "sq" },
    @{ t = "am"; s = "am" },
    @{ t = "az"; s = "az" },
    @{ t = "bn"; s = "bn" },
    @{ t = "bg"; s = "bg" },
    @{ t = "ca"; s = "ca" },
    @{ t = "zh-CN"; s = "zh-rCN" },
    @{ t = "zh-HK"; s = "zh-rHK" },
    @{ t = "zh-TW"; s = "zh-rTW" },
    @{ t = "hr"; s = "hr" },
    @{ t = "cs"; s = "cs" },
    @{ t = "da"; s = "da" },
    @{ t = "nl"; s = "nl" },
    @{ t = "et"; s = "et" },
    @{ t = "fil"; s = "fil" },
    @{ t = "fi"; s = "fi" },
    @{ t = "fr"; s = "fr" },
    @{ t = "de"; s = "de" },
    @{ t = "el"; s = "el" },
    @{ t = "gu"; s = "gu" },
    @{ t = "ha"; s = "ha" },
    @{ t = "he"; s = "he" },
    @{ t = "hi"; s = "hi" },
    @{ t = "hu"; s = "hu" },
    @{ t = "id"; s = "id" },
    @{ t = "ga"; s = "ga" },
    @{ t = "it"; s = "it" },
    @{ t = "ja"; s = "ja" },
    @{ t = "kn"; s = "kn" },
    @{ t = "kk"; s = "kk" },
    @{ t = "ko"; s = "ko" },
    @{ t = "lo"; s = "lo" },
    @{ t = "lv"; s = "lv" },
    @{ t = "lt"; s = "lt" },
    @{ t = "mk"; s = "mk" },
    @{ t = "ms"; s = "ms" },
    @{ t = "ml"; s = "ml" },
    @{ t = "mr"; s = "mr" },
    @{ t = "nb"; s = "nb" },
    @{ t = "om"; s = "om" },
    @{ t = "fa"; s = "fa" },
    @{ t = "pl"; s = "pl" },
    @{ t = "pt-BR"; s = "pt-rBR" },
    @{ t = "pt-PT"; s = "pt-rPT" },
    @{ t = "pa"; s = "pa" },
    @{ t = "ro"; s = "ro" },
    @{ t = "ru"; s = "ru" },
    @{ t = "sr"; s = "sr" },
    @{ t = "sk"; s = "sk" },
    @{ t = "sl"; s = "sl" },
    @{ t = "es"; s = "es" },
    @{ t = "sw"; s = "sw" },
    @{ t = "sv"; s = "sv" },
    @{ t = "ta"; s = "ta" },
    @{ t = "te"; s = "te" },
    @{ t = "th"; s = "th" },
    @{ t = "tr"; s = "tr" },
    @{ t = "uk"; s = "uk" },
    @{ t = "ur"; s = "ur" },
    @{ t = "uz"; s = "uz" },
    @{ t = "vi"; s = "vi" }
)

foreach ($p in $pairs) {
    $destDir = Join-Path $resRoot ("values-" + $p.s)
    $destFile = Join-Path $destDir "strings.xml"
    if (-not (Test-Path $destFile)) {
        New-Item -ItemType Directory -Force -Path $destDir | Out-Null
        Copy-Item -LiteralPath $defaultStrings -Destination $destFile
        Write-Host "Created placeholder: values-$($p.s)/strings.xml (tag $($p.t))"
    }
}

Write-Host "Done."
