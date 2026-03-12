[CmdletBinding()]
param (
    [string]$NodeName,
    [string]$S3Bucket,
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$PSDefaultParameterValues['*:ErrorAction'] = "Stop"

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

if ($Force) {
    write "Delete: s3://$S3Bucket/hsm/"
    & aws s3 rm "s3://$S3Bucket/hsm/" --recursive
} else {
    write "Delete: s3://$S3Bucket/hsm/$NodeName"
    & aws s3api delete-object --bucket "$S3Bucket" --key "hsm/$NodeName"
}
if (-not $?) { throw }

$ParallelCount = (aws s3 ls "s3://$S3Bucket/hsm/" | measure -l).Lines
write "Parallel Count: $ParallelCount"
& aws s3 ls "s3://$S3Bucket/hsm/"

if ($ParallelCount -ne 0) {
    exit 0
}

while ($True) {
    # & aws --profile sign cloudhsmv2 describe-clusters --query 'Clusters[0].Hsms' --no-cli-pager

    $HsmCount = aws --profile sign cloudhsmv2 describe-clusters --query 'length(Clusters[0].Hsms)' --output text
    write "HSM Count: $HsmCount"
    if ($HsmCount -eq 0) {
        break
    }

    $HsmState = aws --profile sign cloudhsmv2 describe-clusters --query 'Clusters[0].Hsms[0].State' --output text
    write "HSM State: $HsmState"
    if ($HsmState -eq 'ACTIVE') {
        write "Delete HSM"
        $HsmEniIp = aws --profile sign cloudhsmv2 describe-clusters --query 'Clusters[0].Hsms[0].EniIp' --output text
        write "HSM EniIp: $HsmEniIp"
        & aws --profile sign cloudhsmv2 delete-hsm --cluster-id 'cluster-zvyumkgp343' --eni-ip $HsmEniIp
    } elseif ($HsmState -eq 'DELETE_IN_PROGRESS') {
        break
    }

    sleep 10
}
