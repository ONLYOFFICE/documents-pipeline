$ErrorActionPreference = "Stop"
$PSDefaultParameterValues['*:ErrorAction'] = "Stop"

$IPv4 = (Test-Connection -ComputerName (hostname) -Count 1).IPV4Address.IPAddressToString
& aws s3 rm "s3://$env:S3_BUCKET/hsm/$IPv4"
if (-not $?) { throw }

$ParallelCount = (aws s3 ls "s3://$env:S3_BUCKET/hsm/" | measure -l).Lines
write "Parallel Count: $ParallelCount"
& aws s3 ls "s3://$env:S3_BUCKET/hsm/"

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
