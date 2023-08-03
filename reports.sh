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

NOW=$(LANG=C TZ=Europe/Moscow date '+%F %R %Z')

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

set -x



msg "${BOLD}${GREEN}PREPARE${NOFORMAT}"

inputf=$1
keys_t=keys.txt
json_d=reports/data.json

rm -rfv reports $keys_t
mkdir -p reports
jq -r '.[].key' $inputf | sort -V > $keys_t
rm -fv $inputf
jq -n '{}' > $json_d

json_add() {
  jq ".$1.$2.$3 += [\"$4\"]" $json_d \
    > $json_d.tmp \
    && mv -f $json_d.tmp $json_d
}

for product in core desktop builder server mobile; do
  if [[ $product == core ]] && grep -q -E "^((windows|mac|linux)/core|closure-maps)/" $keys_t; then
    (grep "^windows/core/" $keys_t || true) | while read key; do
      json_add $product win archive $key
    done
    (grep "^mac/core/" $keys_t || true) | while read key; do
      json_add $product mac archive $key
    done
    (grep "^linux/core/" $keys_t || true) | while read key; do
      json_add $product linux archive $key
    done
    (grep -E "^closure-maps/.*opensource.*/" $keys_t || true) | while read key; do
      json_add $product linux closuremaps_opensource $key
    done
    (grep -E "^closure-maps/.*commercial.*/" $keys_t || true) | while read key; do
      json_add $product linux closuremaps_commercial $key
    done
  elif [[ $product == mobile ]]; then
    (grep -E "^$product/android/" $keys_t || true) | while read key; do
      json_add $product android archive $key
    done
  else
    for platform in win mac linux; do
      for type in generic inno advinst update x86_64 arm v8 debian rhel suse; do
        (grep -E "^$product/$platform/.*$type.*/" $keys_t || true) | while read key; do
          json_add $product $platform $type $key
        done
      done
    done
  fi
done



jq -r "keys_unsorted[]" $json_d | while read product; do
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

  jq -r ".$product | keys_unsorted[]" $json_d | while read platform; do
    echo "  <h2 id=\"$platform\">${PLATFORM_TITLES[$platform]}</h2>" >> $html

    jq -r ".$product.$platform | keys_unsorted[]" $json_d | while read type; do
      echo "  <h3 id=\"$platform-$type\">${TYPE_TITLES[$type]}</h3>" >> $html

      # jq -r ".$product.$platform.$type[]" $json_d | sort -V
      jq -r ".$product.$platform.$type[]" $json_d | while read key; do
        object=$(aws s3api head-object --bucket $S3_BUCKET --key $key)
        size=$(<<<$object jq -r '.ContentLength' | LANG=C numfmt --to=iec-i)
        md5=$(<<<$object jq -r '.Metadata.md5')

        echo "  <div class=\"d-inline-flex width-full\" style=\"gap:8px\">" >> $html
        echo "    <span class=\"flex-1\"><a href=\"$S3_BASE_URL/$key\">${key##*/}</a></span>" >> $html
        echo "    <span class=\"color-fg-muted\">${size}B</span>" >> $html
        # if [[ -n $md5 ]]; then
        #   echo "    <span class=\"\"><code>MD5 Hash: $md5</code></span>" >> $html
        # fi
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
  aws s3 sync --no-progress --acl public-read \
    s3://$S3_BUCKET/reports/$BRANCH_NAME/$BUILD_NUMBER \
    s3://$S3_BUCKET/reports/$BRANCH_NAME/latest

  for f in reports/*; do
    echo "$S3_BASE_URL/reports/$BRANCH_NAME/$BUILD_NUMBER/${f##*/}"
  done
  for f in reports/*; do
    echo "$S3_BASE_URL/reports/$BRANCH_NAME/latest/${f##*/}"
  done
fi
