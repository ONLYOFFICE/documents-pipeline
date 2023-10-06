#!/usr/bin/env bash

set -Eeuo pipefail
cd "${0%/*}"

setup_colors() {
  if [[ -z "${NO_COLOR-}" ]]; then
    NOFORMAT='\033[0m' BOLD='\033[1m'
    RED='\033[0;31m' GREEN='\033[0;32m' ORANGE='\033[0;33m' BLUE='\033[0;34m'
    PURPLE='\033[0;35m' CYAN='\033[0;36m' YELLOW='\033[1;33m'
  else
    NOFORMAT='' BOLD='' RED='' GREEN='' ORANGE='' BLUE='' PURPLE='' CYAN='' YELLOW=''
  fi
}

msg() {
  echo >&2 -e "${1-}"
}

setup_colors

set -x

declare -A PLATFORM_TITLES=(
  [win]="Windows"
  [mac]="macOS"
  [linux]="Linux"
  [android]="Android"
  [other]="Other"
)

declare -A PRODUCT_TITLES=(
  [core]="Core"
  [desktop]="Desktop Editors"
  [builder]="Document Builder"
  [server]="Document Server"
  [mobile]="Mobile"
)

declare -A TYPE_TITLES=(
  [archive]="Archive"
  [closuremaps_opensource]="Closure maps opensource"
  [closuremaps_commercial]="Closure maps commercial"
  [generic]="Portable"
  [update]="Update"
  [inno]="Inno Setup"
  [advinst]="Advanced Installer"
  [x86_64]="x86_64"
  [v8]="x86_64 V8"
  [arm]="arm64"
  [altlinux]="ALT Linux"
  [appimage]="AppImage"
  [astra]="Astra Linux Special Edition"
  [debian]="Debian / Ubuntu"
  [flatpak]="Flatpak"
  [rhel]="RHEL / CentOS"
  [rosa]="ROSA"
  [snap]="Snapcraft"
  [suse]="SUSE Linux / OpenSUSE"
)

NOW=$(LANG=C date '+%F %R %Z')
keys_t=${1:-keys.txt}
data_j=reports/data.json
desc_h=build.html

rm -rfv reports $desc_h
mkdir -p reports

msg "${BOLD}${GREEN}PREPARE${NOFORMAT}"

echo COMPANY_NAME=$COMPANY_NAME
echo BRANCH_NAME=$BRANCH_NAME
echo BUILD_VERSION=$BUILD_VERSION
echo BUILD_NUMBER=$BUILD_NUMBER
echo S3_BASE_URL=$S3_BASE_URL
echo S3_BUCKET=$S3_BUCKET
[[ ! -f $keys_t ]] && exit 1
jq -n {} > $data_j

json_add() {
  jq ".$1.$2.$3 += [\"$4\"]" $data_j \
    > $data_j.tmp \
    && mv -f $data_j.tmp $data_j
}

for product in core desktop builder server mobile; do
  if [[ $product == core ]] && grep -q -E "^((windows|mac|linux)/core|closure-maps)/" $keys_t; then
    (grep "^windows/core/" $keys_t || :) | while read key; do
      json_add $product win archive $key
    done
    (grep "^mac/core/" $keys_t || :) | while read key; do
      json_add $product mac archive $key
    done
    (grep "^linux/core/" $keys_t || :) | while read key; do
      json_add $product linux archive $key
    done
    (grep "^closure-maps/opensource/" $keys_t || :) | while read key; do
      json_add $product linux closuremaps_opensource $key
    done
    (grep "^closure-maps/commercial/" $keys_t || :) | while read key; do
      json_add $product linux closuremaps_commercial $key
    done
  elif [[ $product == mobile ]]; then
    (grep "^$product/android/" $keys_t || :) | while read key; do
      json_add $product android archive $key
    done
  else
    for platform in win mac linux; do
      for type in archive generic update inno advinst x86_64 v8 arm \
                  altlinux appimage astra debian flatpak rhel rosa snap suse; do
        (grep "^$product/$platform/$type/" $keys_t || :) | while read key; do
          json_add $product $platform $type $key
        done
      done
    done
  fi
done



jq -r "keys_unsorted[]" $data_j | while read product; do
  msg "${BOLD}${GREEN}${product^^}${NOFORMAT}"
  html=reports/$product.html

  cat << EOF > $html
<!DOCTYPE html>
<html lang="en-US">
<head>
  <title>$COMPANY_NAME ${PRODUCT_TITLES[$product]} - $BRANCH_NAME - $BUILD_NUMBER</title>
  <link rel="shortcut icon" sizes="16x16" href="$S3_BASE_URL/favicon.png" type="image/png">
  <link rel="stylesheet" href="https://unpkg.com/@primer/css@21.0.7/dist/primer.css">
</head>
<body>
  <div class="container-lg px-3 my-5 markdown-body">
  <h1>$COMPANY_NAME ${PRODUCT_TITLES[$product]} - $BRANCH_NAME - $BUILD_NUMBER</h1>
  <p class ="color-fg-muted">$NOW</p>
EOF

  jq -r ".$product | keys_unsorted[]" $data_j | while read platform; do
    echo "  <h2 id=\"$platform\">${PLATFORM_TITLES[$platform]}</h2>" >> $html

    jq -r ".$product.$platform | keys_unsorted[]" $data_j | while read type; do
      echo "  <h3 id=\"$platform-$type\">${TYPE_TITLES[$type]}</h3>" >> $html

      # jq -r ".$product.$platform.$type[]" $data_j | sort -V
      jq -r ".$product.$platform.$type[]" $data_j | while read key; do
        object=$(aws s3api head-object --bucket $S3_BUCKET --key $key || jq -n {})
        size=$(<<<$object jq -er '.ContentLength' || echo -n 0)
        sha256=$(<<<$object jq -er '.Metadata.sha256' || :)
        sha1=$(<<<$object jq -er '.Metadata.sha1' || :)
        md5=$(<<<$object jq -er '.Metadata.md5' || :)

        echo "  <div class=\"d-inline-flex width-full\" style=\"gap:8px\">" >> $html
        echo "    <span class=\"flex-1\"><a href=\"$S3_BASE_URL/$key\">${key##*/}</a></span>" >> $html
        echo "    <span class=\"color-fg-muted\">$(LANG=C numfmt --to=iec-i $size)B</span>" >> $html
        if [[ -n $sha256 ]]; then
          echo "    <span class=\"tooltipped tooltipped-nw tooltipped-no-delay\" aria-label=\"$sha256\">SHA256</span>" >> $html
        fi
        if [[ -n $sha1 ]]; then
          echo "    <span class=\"tooltipped tooltipped-nw tooltipped-no-delay\" aria-label=\"$sha1\">SHA1</span>" >> $html
        fi
        if [[ -n $md5 ]]; then
          echo "    <span class=\"tooltipped tooltipped-nw tooltipped-no-delay\" aria-label=\"$md5\">MD5</span>" >> $html
        fi
        echo "  </div>" >> $html
      done
    done
  done

  cat << EOF >> $html
  </div>
  <script src="https://cdnjs.cloudflare.com/ajax/libs/anchor-js/5.0.0/anchor.min.js"></script>
  <script>anchors.add('');</script>
</body>
</html>
EOF
done



msg "${BOLD}${GREEN}UPLOAD${NOFORMAT}"

if ls reports/*.html 2> /dev/null; then
  aws s3 sync --no-progress --acl public-read \
    reports \
    s3://$S3_BUCKET/reports/$BRANCH_NAME/$BUILD_NUMBER
  aws s3 sync --no-progress --acl public-read --delete \
    s3://$S3_BUCKET/reports/$BRANCH_NAME/$BUILD_NUMBER \
    s3://$S3_BUCKET/reports/$BRANCH_NAME/latest

  for f in reports/*; do
    echo "URL: $S3_BASE_URL/reports/$BRANCH_NAME/$BUILD_NUMBER/${f##*/}"
  done
  for f in reports/*; do
    echo "URL: $S3_BASE_URL/reports/$BRANCH_NAME/latest/${f##*/}"
  done

  for product in core desktop builder server mobile; do
    [[ -f reports/$product.html ]] || continue
    if [[ -f $desc_h ]]; then
      echo -n ' \ ' >> $desc_h
    fi
    echo -n "<a href=\"$S3_BASE_URL/reports/$BRANCH_NAME/$BUILD_NUMBER/$product.html\" target=\"_blank\">${product^}</a>" >> $desc_h
  done
fi
