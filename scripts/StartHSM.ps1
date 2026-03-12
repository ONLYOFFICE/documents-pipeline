[CmdletBinding()]
param (
    [string]$NodeName,
    [string]$S3Bucket
)

$ErrorActionPreference = "Stop"
$PSDefaultParameterValues['*:ErrorAction'] = "Stop"

while ($True) {
    # & aws --profile sign cloudhsmv2 describe-clusters --query 'Clusters[0].Hsms' --no-cli-pager

    $HsmCount = aws --profile sign cloudhsmv2 describe-clusters --query 'length(Clusters[0].Hsms)' --output text
    write "HSM Count: $HsmCount"
    if ($HsmCount -eq 0) {
        write "Create HSM"
        & aws --profile sign cloudhsmv2 create-hsm --cluster-id 'cluster-zvyumkgp343' --availability-zone 'eu-central-1a'
        if (-not $?) { throw }
    } else {
        write "HSM exists"
    }

    $HsmState = aws --profile sign cloudhsmv2 describe-clusters --query 'Clusters[0].Hsms[0].State' --output text
    write "HSM State: $HsmState"
    if ($HsmState -eq 'ACTIVE') {
        break
    }

    sleep 10
}

$HsmEniIp = aws --profile sign cloudhsmv2 describe-clusters --query 'Clusters[0].Hsms[0].EniIp' --output text
write "HSM EniIp: $HsmEniIp"

& configure-cli -a $HsmEniIp --disable-key-availability-check
if (-not $?) { throw }
& configure-pkcs11 -a $HsmEniIp --disable-key-availability-check
if (-not $?) { throw }

if($NodeName -eq "") {
    if (Test-Path "env:NODE_NAME") {
        $NodeName = $env:NODE_NAME
    } else {
        $NodeName = hostname
    }
}

if($S3Bucket -eq "") {
    if (Test-Path "env:S3_BUCKET") {
        $S3Bucket = $env:S3_BUCKET
    } else {
        $S3Bucket = "repo-doc-onlyoffice-com"
    }
}

write "Upload: s3://$S3Bucket/hsm/$NodeName"
& aws s3api put-object --bucket "$S3Bucket" --key "hsm/$NodeName" --content-length 0
if (-not $?) { throw }
