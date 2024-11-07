#!/usr/bin/env bash

set -Eeuo pipefail

cd "${0%/*}"

usage() {
  cat << EOF
Usage: ./${0##*/} [OPTION]
Build artifacts reports.

      --json                   build JSON only
      --html                   build HTML from JSON only
  -c, --company                var COMPANY_NAME (optional)
  -b, --branch                 var BRANCH_NAME
  -r, --version                var BUILD_VERSION (optional)
  -n, --number                 var BUILD_NUMBER
  -s, --s3-bucket              var S3_BUCKET (optional)
  -u, --s3-base-url            var S3_BASE_URL (optional)
  -v, --verbose                print script debug info
  -h, --help                   print this help and exit
EOF
  exit
}

setup_colors() {
  if [[ -z "${NO_COLOR-}" ]]; then
    NOFORMAT='\033[0m'  BOLD='\033[1m'      RED='\033[0;31m'
    GREEN='\033[0;32m'  ORANGE='\033[0;33m' BLUE='\033[0;34m'
    PURPLE='\033[0;35m' CYAN='\033[0;36m'   YELLOW='\033[1;33m'
  else
    NOFORMAT=''         BOLD=''             RED=''
    GREEN=''            ORANGE=''           BLUE=''
    PURPLE=''           CYAN=''             YELLOW=''
  fi
}

msg() {
  echo >&2 -e "${1-}"
}

die() {
  local msg=$1
  local code=${2-1}
  msg "$msg"
  exit "$code"
}

parse_params() {
  COMPANY_NAME="ONLYOFFICE"
  S3_BUCKET="repo-doc-onlyoffice-com"
  S3_BASE_URL="https://s3.eu-west-1.amazonaws.com/$S3_BUCKET"

  while :; do
    case "${1-}" in
      -h | --help)             usage ;;
      -v | --verbose)          set -x ;;
      --no-color)              NO_COLOR=1 ;;
      --json)                  JSON_ONLY=1 ;;
      --html)                  HTML_ONLY=1 ;;
      -c | --company)          COMPANY_NAME="${2-}"  ; shift ;;
      -b | --branch)           BRANCH_NAME="${2-}"   ; shift ;;
      -r | --version)          BUILD_VERSION="${2-}" ; shift ;;
      -n | --number)           BUILD_NUMBER="${2-}"  ; shift ;;
      -s | --s3-bucket)        S3_BUCKET="${2-}"     ; shift ;;
      -?*)                     die "Unknown option: $1" ;;
      *)                       break ;;
    esac
    shift
  done

  args=("$@")

  [[ -z "${BRANCH_NAME-}"  ]] && die "Missing parameter: BRANCH_NAME"
  [[ -z "${BUILD_NUMBER-}" ]] && die "Missing parameter: BUILD_NUMBER"
  if [[ -z "${BUILD_VERSION-}" ]]; then
    case "$BRANCH_NAME" in
      release/v* | hotfix/v* ) BUILD_VERSION="${BRANCH_NAME##*/v}" ;;
      develop )                BUILD_VERSION="99.99.99" ;;
      *)                       die "Missing parameter: BUILD_VERSION" ;;
    esac
  fi

  COMPANY_NAME_LC="${COMPANY_NAME,,}"
  VERSION="$BUILD_VERSION-$BUILD_NUMBER"
  VERSION_DOT="$BUILD_VERSION.$BUILD_NUMBER"

  return 0
}

parse_params "$@"
setup_colors

msg "${BOLD}Read parameters:${NOFORMAT}"
msg "- COMPANY_NAME:     $COMPANY_NAME"
msg "- BRANCH_NAME:      $BRANCH_NAME"
msg "- BUILD_VERSION:    $BUILD_VERSION"
msg "- BUILD_NUMBER:     $BUILD_NUMBER"
msg "- S3_BUCKET:        $S3_BUCKET"
msg "- S3_BASE_URL:      $S3_BASE_URL"

json=reports/data.json
desc=build.html
now=$(LANG=C TZ=Etc/GMT-3 date '+%F %R %Z')

json_add() {
  msg "$BLUE$1 > $2 > $3$NOFORMAT"
  aws s3api list-objects-v2 --bucket $S3_BUCKET --prefix $4 \
    --query 'Contents[].Key' | jq -r '.[]?' | while read key; do
    msg "  $key"
    object=$(aws s3api head-object --bucket $S3_BUCKET --key $key || jq -n {})
    size=$(<<<$object jq -er '.ContentLength // 0')
    sha256=$(<<<$object jq -er '.Metadata.sha256 // empty' || :)
    sha1=$(<<<$object jq -er '.Metadata.sha1 // empty' || :)
    md5=$(<<<$object jq -er '.Metadata.md5 // empty' || :)
    jq ".$1.$2.$3 += [{
      key: \"$key\",
      size: $size,
      sha256: \"$sha256\",
      sha1: \"$sha1\",
      md5: \"$md5\"
    }]" $json \
      > $json.tmp && mv -f $json.tmp $json
  done
}

if [[ -z "${HTML_ONLY-}" ]]; then

  msg
  msg "${BOLD}JSON:${NOFORMAT}"
  rm -rfv $desc reports
  mkdir -pv reports
  jq -n '{}' > $json

  # CORE
  json_add core win   archive archive/$BRANCH_NAME/$BUILD_NUMBER/core-win
  json_add core mac   archive archive/$BRANCH_NAME/$BUILD_NUMBER/core-mac
  json_add core linux archive archive/$BRANCH_NAME/$BUILD_NUMBER/core-linux
  json_add core linux closuremaps_sdkjs_opensource closure-maps/sdkjs/opensource/$BUILD_VERSION/$BUILD_NUMBER
  json_add core linux closuremaps_sdkjs_commercial closure-maps/sdkjs/commercial/$BUILD_VERSION/$BUILD_NUMBER
  json_add core linux closuremaps_webapps          closure-maps/web-apps/opensource/$BUILD_VERSION/$BUILD_NUMBER

  # DESKTOP
  json_add desktop win   generic desktop/win/generic/${COMPANY_NAME}-DesktopEditors-${VERSION_DOT}
  json_add desktop win   inno    desktop/win/inno/${COMPANY_NAME}-DesktopEditors-${VERSION_DOT}
  json_add desktop win   inno    desktop/win/inno/${COMPANY_NAME}-DesktopEditors-Standalone-${VERSION_DOT}
  json_add desktop win   inno    desktop/win/inno/${COMPANY_NAME}-DesktopEditors-Update-${VERSION_DOT}
  json_add desktop win   advinst desktop/win/advinst/${COMPANY_NAME}-DesktopEditors-${VERSION_DOT}
  json_add desktop win   update  desktop/win/update/$BUILD_VERSION/$BUILD_NUMBER
  json_add desktop win   online  desktop/win/online/OnlineInstaller-${VERSION_DOT}
  json_add desktop mac   arm     desktop/mac/arm/$BUILD_VERSION/$BUILD_NUMBER
  json_add desktop mac   x86_64  desktop/mac/x86_64/$BUILD_VERSION/$BUILD_NUMBER
  json_add desktop mac   v8      desktop/mac/v8/$BUILD_VERSION/$BUILD_NUMBER
  json_add desktop linux generic desktop/linux/generic/${COMPANY_NAME_LC}-desktopeditors-${VERSION}
  json_add desktop linux generic desktop/linux/generic/${COMPANY_NAME_LC}-desktopeditors-help-${VERSION}
  json_add desktop linux debian  desktop/linux/debian/${COMPANY_NAME_LC}-desktopeditors_${VERSION}
  json_add desktop linux debian  desktop/linux/debian/${COMPANY_NAME_LC}-desktopeditors-help_${VERSION}
  json_add desktop linux rhel    desktop/linux/rhel/${COMPANY_NAME_LC}-desktopeditors-${VERSION}
  json_add desktop linux rhel    desktop/linux/rhel/${COMPANY_NAME_LC}-desktopeditors-help-${VERSION}
  json_add desktop linux suse    desktop/linux/suse/${COMPANY_NAME_LC}-desktopeditors-${VERSION}
  json_add desktop linux suse    desktop/linux/suse/${COMPANY_NAME_LC}-desktopeditors-help-${VERSION}

  # BUILDER
  json_add builder win   archive archive/$BRANCH_NAME/$BUILD_NUMBER/builder-win
  json_add builder mac   archive archive/$BRANCH_NAME/$BUILD_NUMBER/builder-mac
  json_add builder linux archive archive/$BRANCH_NAME/$BUILD_NUMBER/builder-linux
  json_add builder win   generic builder/win/generic/${COMPANY_NAME}-DocumentBuilder-${VERSION_DOT}
  json_add builder win   inno    builder/win/inno/${COMPANY_NAME}-DocumentBuilder-${VERSION_DOT}
  json_add builder mac   generic builder/mac/generic/${COMPANY_NAME_LC}-documentbuilder-${VERSION}
  json_add builder linux generic builder/linux/generic/${COMPANY_NAME_LC}-documentbuilder-${VERSION}
  json_add builder linux debian  builder/linux/debian/${COMPANY_NAME_LC}-documentbuilder_${VERSION}
  json_add builder linux rhel    builder/linux/rhel/${COMPANY_NAME_LC}-documentbuilder-${VERSION}

  # SERVER
  json_add server win   inno   server/win/inno/${COMPANY_NAME}-DocumentServer-${VERSION_DOT}
  json_add server win   inno   server/win/inno/${COMPANY_NAME}-DocumentServer-EE-${VERSION_DOT}
  json_add server win   inno   server/win/inno/${COMPANY_NAME}-DocumentServer-DE-${VERSION_DOT}
  json_add server linux debian server/linux/debian/${COMPANY_NAME_LC}-documentserver_${VERSION}
  json_add server linux debian server/linux/debian/${COMPANY_NAME_LC}-documentserver-ee_${VERSION}
  json_add server linux debian server/linux/debian/${COMPANY_NAME_LC}-documentserver-de_${VERSION}
  json_add server linux rhel   server/linux/rhel/${COMPANY_NAME_LC}-documentserver-${VERSION}
  json_add server linux rhel   server/linux/rhel/${COMPANY_NAME_LC}-documentserver-ee-${VERSION}
  json_add server linux rhel   server/linux/rhel/${COMPANY_NAME_LC}-documentserver-de-${VERSION}
  json_add server linux snap   server/linux/snap/${COMPANY_NAME_LC}-documentserver-${VERSION}
  json_add server linux snap   server/linux/snap/${COMPANY_NAME_LC}-documentserver-ee-${VERSION}
  json_add server linux snap   server/linux/snap/${COMPANY_NAME_LC}-documentserver-de-${VERSION}

  # MOBILE
  json_add mobile android archive mobile/android/build-${VERSION}

fi

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
  [closuremaps_webapps]="WEB-APPS Closure Maps"
  [generic]="Portable"
  [update]="Update"
  [inno]="Inno Setup"
  [advinst]="Advanced Installer"
  [online]="Online Installer"
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

if [[ -z "${JSON_ONLY-}" ]]; then

  msg
  msg "${BOLD}HTML:${NOFORMAT}"
  rm -rfv $desc reports/*.html

  jq -r "keys_unsorted[]" $json | while read product; do

    msg "$BLUE$product$NOFORMAT"
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
  <p class ="color-fg-muted">$now</p>
EOF

    jq -r ".$product | keys_unsorted[]" $json | while read platform; do

      msg "  $GREEN$platform$NOFORMAT"
      echo "  <h2 id=\"$platform\">${PLATFORM_TITLES[$platform]}</h2>" >> $html

      jq -r ".$product.$platform | keys_unsorted[]" $json | while read type; do

        msg "    $YELLOW$type$NOFORMAT"
        echo "  <h3 id=\"$platform-$type\">${TYPE_TITLES[$type]}</h3>" >> $html
        length=$(jq -r ".$product.$platform.$type | length" $json)
        for ((i = 0 ; i < $length ; i++)); do

          obj=$(jq -r ".$product.$platform.$type[$i]" $json)
          key=$(<<<$obj jq -r ".key")
          size=$(<<<$obj jq -r ".size // empty")
          sha256=$(<<<$obj jq -r ".sha256 // empty")
          sha1=$(<<<$obj jq -r ".sha1 // empty")
          md5=$(<<<$obj jq -r ".md5 // empty")
          msg "      $key"

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

    [[ -f $desc ]] && echo -n ' &centerdot; ' >> $desc
    echo -n "<a href=\"$S3_BASE_URL/reports/$BRANCH_NAME/$BUILD_NUMBER" >> $desc
    echo -n "/$product.html\" target=\"_blank\">${product^}</a>" >> $desc

  done

  msg
  msg "${BOLD}Upload:${NOFORMAT}"

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

fi
