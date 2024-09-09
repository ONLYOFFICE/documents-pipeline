#!/usr/bin/env bash

set -Eeuo pipefail
cd "${0%/*}"

setup_colors() {
  if [[ -z "${NO_COLOR-}" ]]; then
    NOFORMAT='\033[0m'
    BOLD='\033[1m'
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    ORANGE='\033[0;33m'
    BLUE='\033[0;34m'
    PURPLE='\033[0;35m'
    CYAN='\033[0;36m'
    YELLOW='\033[1;33m'
  else
    NOFORMAT=''
    BOLD=''
    RED=''
    GREEN=''
    ORANGE=''
    BLUE=''
    PURPLE=''
    CYAN=''
    YELLOW=''
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
  [closuremaps_sdkjs_opensource]="SDKJS Closure Maps Opensource"
  [closuremaps_sdkjs_commercial]="SDKJS Closure Maps Commercial"
  [closuremaps_webapps_opensource]="WEB-APPS Closure Maps"
  # [closuremaps_webapps_commercial]="WEB-APPS Closure Maps Commercial"
  [generic]="Portable"
  [update]="Update"
  [inno]="Inno Setup"
  [advinst]="Advanced Installer"
  [x86_64]="x86_64"
  [v8]="x86_64 V8"
  [arm]="arm64"
  [appimage]="AppImage"
  [debian]="Debian / Ubuntu"
  [flatpak]="Flatpak"
  [rhel]="RHEL / CentOS"
  [snap]="Snapcraft"
  [suse]="SUSE Linux / OpenSUSE"
)

NOW=$(LANG=C date '+%F %R %Z')
keys_t=${1:-keys.txt}
data_j=reports/data.json
desc_h=build.html

msg "${BOLD}${GREEN}PREPARE${NOFORMAT}"

echo COMPANY_NAME=$COMPANY_NAME
echo BRANCH_NAME=$BRANCH_NAME
echo BUILD_VERSION=$BUILD_VERSION
echo BUILD_NUMBER=$BUILD_NUMBER
echo S3_BASE_URL=$S3_BASE_URL
echo S3_BUCKET=$S3_BUCKET

rm -rfv reports $desc_h
mkdir -p reports

[[ ! -f $keys_t ]] && exit 1
jq -n {} > $data_j

json_add() {
  jq ".$1.$2.$3 += [\"$4\"]" $data_j \
    > $data_j.tmp \
    && mv -f $data_j.tmp $data_j
}

(grep "^archive/.*/core-win" $keys_t || :) | while read key; do
  json_add core win archive $key
done
(grep "^archive/.*/core-mac" $keys_t || :) | while read key; do
  json_add core mac archive $key
done
(grep "^archive/.*/core-linux" $keys_t || :) | while read key; do
  json_add core linux archive $key
done

(grep "^archive/.*/builder-win" $keys_t || :) | while read key; do
  json_add builder win archive $key
done
(grep "^archive/.*/builder-mac" $keys_t || :) | while read key; do
  json_add builder mac archive $key
done
(grep "^archive/.*/builder-linux" $keys_t || :) | while read key; do
  json_add builder linux archive $key
done

(grep "^closure-maps/sdkjs/opensource/" $keys_t || :) | while read key; do
  json_add core linux closuremaps_sdkjs_opensource $key
done
(grep "^closure-maps/sdkjs/commercial/" $keys_t || :) | while read key; do
  json_add core linux closuremaps_sdkjs_commercial $key
done
(grep "^closure-maps/web-apps/opensource/" $keys_t || :) | while read key; do
  json_add core linux closuremaps_webapps_opensource $key
done
# (grep "^closure-maps/web-apps/commercial/" $keys_t || :) | while read key; do
#   json_add core linux closuremaps_webapps_commercial $key
# done

(grep "^mobile/android/" $keys_t || :) | while read key; do
  json_add mobile android archive $key
done

for product in desktop builder server; do
  for platform in win mac linux; do
    for type in archive generic update inno advinst x86_64 v8 arm \
                appimage debian flatpak rhel snap suse; do
      (grep "^$product/$platform/$type/" $keys_t || :) | while read key; do
        json_add $product $platform $type $key
      done
    done
  done
done



jq -r "keys_unsorted[]" $data_j | while read product; do
  msg "${BOLD}${GREEN}${product^^}${NOFORMAT}"
  html=reports/$product.html

  cat << EOF > $html
<!DOCTYPE html>
<html lang="en-US">
<head>
  <title>$COMPANY_NAME ${PRODUCT_TITLES[$product]} - $BRANCH_NAME - $BUILD_NUMBER</title>
  <link rel="shortcut icon" href="$S3_BASE_URL/favicon.ico" type="image/x-icon">
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
        size=$(<<<$object jq -er '.ContentLength // 0')
        sha256=$(<<<$object jq -er '.Metadata.sha256 // empty' || :)
        sha1=$(<<<$object jq -er '.Metadata.sha1 // empty' || :)
        md5=$(<<<$object jq -er '.Metadata.md5 // empty' || :)

        if [[ -z $sha256 && -z $sha1 && -z $md5 ]]; then
          echo "  <p class=\"m-0\">" >> $html
          echo "    <a href=\"$S3_BASE_URL/$key\">${key##*/}</a>" >> $html
          echo "    $(LANG=C numfmt --to=iec-i $size)B" >> $html
          echo "  </p>" >> $html
        else
          echo "  <details class=\"m-0\">" >> $html
          echo "    <summary class=\"list-style-none\">" >> $html
          echo "      <a href=\"$S3_BASE_URL/$key\">${key##*/}</a>" >> $html
          echo "      $(LANG=C numfmt --to=iec-i $size)B" >> $html
          echo "      <div class=\"dropdown-caret\"></div>" >> $html
          echo "    </summary>" >> $html
          echo "    <pre class=\"m-0\">SHA256: $sha256<br>SHA1: $sha1<br>MD5: $md5</pre>" >> $html
          echo "  </details>" >> $html
        fi
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
    [[ -f $desc_h ]] && echo -n ' &centerdot; ' >> $desc_h
    echo -n "<a href=\"$S3_BASE_URL/reports/$BRANCH_NAME/$BUILD_NUMBER/$product.html\" target=\"_blank\">${product^}</a>" >> $desc_h
  done
fi
