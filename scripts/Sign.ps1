[CmdletBinding()]
param (
    [string]$CertFile,
    [string]$HsmCreds,
    [string]$TimestampServer,
    [string]$File
)

$ErrorActionPreference = "Stop"
$PSDefaultParameterValues['*:ErrorAction'] = "Stop"

if($CertFile -eq "") {
    if (Test-Path "env:WINDOWS_HSM_CERTIFICATE") {
        $CertFile = $env:WINDOWS_HSM_CERTIFICATE
    } else {
        throw
    }
}

if($HsmCreds -eq "") {
    if (Test-Path "env:WINDOWS_HSM_USERPASS") {
        $HsmCreds = $env:WINDOWS_HSM_USERPASS
    } else {
        throw
    }
}

if($TimestampServer -eq "") {
    if (Test-Path "env:WINDOWS_TIMESTAMP_SERVER") {
        $TimestampServer = $env:WINDOWS_TIMESTAMP_SERVER
    } else {
        $TimestampServer = "http://timestamp.digicert.com"
    }
}

if($File -eq "") {
    throw
}

$env:OPENSSL_ENGINES = "C:\Program Files\Amazon\CloudHSM\lib"
$FileSigned = "$env:TMP\tmp_signed"

if (Test-Path "$FileSigned") {
    # write "Delete: $FileSigned"
    ri -Force "$FileSigned"
}

write "Sign: $File"
& osslsigncode sign `
    -pkcs11engine "pkcs11" `
    -pkcs11module "$env:OPENSSL_ENGINES\cloudhsm_pkcs11.dll" `
    -certs "$CertFile" `
    -key "pkcs11:token=hsm1;object=RSASignPriv1;pin-value=$HsmCreds" `
    -t "$TimestampServer" `
    -in "$File" `
    -out "$FileSigned" `
    -nolegacy `
    -h sha256
if (-not $?) { throw }

$Status = (Get-AuthenticodeSignature "$FileSigned").Status
if ($Status -ne 'Valid') { throw }

write "Move: $FileSigned > $File"
mi -Path "$FileSigned" -Destination "$File" -Force
