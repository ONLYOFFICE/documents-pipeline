$ErrorActionPreference = "Stop"
$PSDefaultParameterValues['*:ErrorAction'] = "Stop"

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
