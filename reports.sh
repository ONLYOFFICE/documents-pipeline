#!/usr/bin/env bash

set -Eeuo pipefail

cd "${0%/*}"

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
  : "${COMPANY_NAME:=ONLYOFFICE}"
  : "${S3_BUCKET:=repo-doc-onlyoffice-com}"
  : "${S3_BASE_URL:=https://s3.eu-west-1.amazonaws.com/repo-doc-onlyoffice-com}"
  REPORTS_DIR=reports
  S3_TEMP_DIR=s3
  JSON=0
  HTML=0
  DEPLOY=0
  JOBS=8

  while :; do
    case "${1-}" in
      -v | --verbose )
        set -x
        ;;
      --no-color )
        NO_COLOR=1
        ;;
      -b | --branch )
        BRANCH_NAME="${2-}"
        shift
        ;;
      -r | --version )
        BUILD_VERSION="${2-}"
        shift
        ;;
      -n | --number )
        BUILD_NUMBER="${2-}"
        shift
        ;;
      --json )
        JSON=1
        ;;
      --html )
        HTML=1
        ;;
      -d | --deploy )
        DEPLOY=1
        ;;
      -j | --jobs )
        JOBS="${2-}"
        shift
        ;;
      -?*)
        echo "Unknown option: $1" >&2 ;;
      *)
        break ;;
    esac
    shift
  done

  [[ -z "${BRANCH_NAME-}"  ]] && die "Missing parameter: BRANCH_NAME"
  [[ -z "${BUILD_NUMBER-}" ]] && die "Missing parameter: BUILD_NUMBER"
  if [[ -z "${BUILD_VERSION-}" ]]; then
    case "$BRANCH_NAME" in
      release/v* | hotfix/v* ) BUILD_VERSION="${BRANCH_NAME##*/v}" ;;
      develop )                BUILD_VERSION="99.99.99" ;;
      *)                       die "Missing parameter: BUILD_VERSION" ;;
    esac
  fi

  DATE_NOW=$(LANG=C TZ=Etc/GMT-3 date '+%F %R %Z')
  VERSION="$BUILD_VERSION-$BUILD_NUMBER"
  VERSION_DOT="$BUILD_VERSION.$BUILD_NUMBER"

  if [[ -z "${NO_COLOR-}" ]]; then
    NOFORMAT='\033[0m'  BOLD='\033[1m'      RED='\033[0;31m'
    GREEN='\033[0;32m'  ORANGE='\033[0;33m' BLUE='\033[0;34m'
    PURPLE='\033[0;35m' CYAN='\033[0;36m'   YELLOW='\033[1;33m'
  else
    NOFORMAT=''         BOLD=''             RED=''
    GREEN=''            ORANGE=''           BLUE=''
    PURPLE=''           CYAN=''             YELLOW=''
  fi

  return 0
}

parse_params "$@"

msg "- BRANCH_NAME:      $BOLD$BRANCH_NAME$NOFORMAT"
msg "- BUILD_VERSION:    $BOLD$BUILD_VERSION$NOFORMAT"
msg "- BUILD_NUMBER:     $BOLD$BUILD_NUMBER$NOFORMAT"
msg "- COMPANY_NAME:     $BOLD$COMPANY_NAME$NOFORMAT"
msg "- S3_BUCKET:        $BOLD$S3_BUCKET$NOFORMAT"
msg "- S3_BASE_URL:      $BOLD$S3_BASE_URL$NOFORMAT"

list=$REPORTS_DIR/data.tsv
json=$REPORTS_DIR/data.json
desc=build.html

mkdir -pv $REPORTS_DIR
> $list
rm -rfv $S3_TEMP_DIR
if [[ "${JSON-}" -eq 1 ]]; then
  echo '{}' > $json
fi
if [[ "${HTML-}" -eq 1 ]]; then
  rm -rfv $REPORTS_DIR/*.html $desc
fi

while read product platform type prefix filter; do
  msg "$BLUE$product > $platform > $type $GREEN[$prefix]${filter:+ $YELLOW[$filter]}$NOFORMAT"
  aws s3api list-objects-v2 --bucket $S3_BUCKET --prefix $prefix \
    --query "Contents[${filter:+?contains(Key,'$filter')}].Key" \
    | jq -r '.[]?' | while read key; do
    msg "  $key"
    echo -e "$product\t$platform\t$type\t$key" >> $list
  done
done << EOF
core      win       archive       archive/$BRANCH_NAME/$BUILD_NUMBER/core-win
core      mac       archive       archive/$BRANCH_NAME/$BUILD_NUMBER/core-mac
core      linux     archive       archive/$BRANCH_NAME/$BUILD_NUMBER/core-linux
builder   win       archive       archive/$BRANCH_NAME/$BUILD_NUMBER/builder-win
builder   mac       archive       archive/$BRANCH_NAME/$BUILD_NUMBER/builder-mac
builder   linux     archive       archive/$BRANCH_NAME/$BUILD_NUMBER/builder-linux
core      linux     cm_sdkjs_oss  closure-maps/sdkjs/opensource/$BUILD_VERSION/$BUILD_NUMBER
core      linux     cm_sdkjs_com  closure-maps/sdkjs/commercial/$BUILD_VERSION/$BUILD_NUMBER
core      linux     cm_webapps    closure-maps/web-apps/opensource/$BUILD_VERSION/$BUILD_NUMBER
desktop   win       generic       desktop/win/generic/    $VERSION_DOT
desktop   win       inno          desktop/win/inno/       $VERSION_DOT
desktop   win       advinst       desktop/win/advinst/    $VERSION_DOT
desktop   win       online        desktop/win/online/     $VERSION_DOT
desktop   win       update        desktop/win/update/$BUILD_VERSION/$BUILD_NUMBER
desktop   mac       arm           desktop/mac/arm/$BUILD_VERSION/$BUILD_NUMBER
desktop   mac       x86_64        desktop/mac/x86_64/$BUILD_VERSION/$BUILD_NUMBER
desktop   mac       v8            desktop/mac/v8/$BUILD_VERSION/$BUILD_NUMBER
desktop   linux     generic       desktop/linux/generic/  $VERSION
desktop   linux     debian        desktop/linux/debian/   $VERSION
desktop   linux     rhel          desktop/linux/rhel/     $VERSION
desktop   linux     suse          desktop/linux/suse/     $VERSION
builder   win       generic       builder/win/generic/    $VERSION_DOT
builder   win       python        builder/win/python/     $VERSION_DOT
builder   mac       generic       builder/mac/generic/    $VERSION
builder   mac       python        builder/mac/python/     $VERSION_DOT
builder   linux     generic       builder/linux/generic/  $VERSION
builder   linux     python        builder/linux/python/   $VERSION_DOT
server    win       inno          server/win/inno/        $VERSION_DOT
server    linux     debian        server/linux/debian/    $VERSION
server    linux     rhel          server/linux/rhel/      $VERSION
server    linux     snap          server/linux/snap/      $VERSION
mobile    android   archive       mobile/android/build-$VERSION
EOF
# builder   win       inno          builder/win/inno/       $VERSION_DOT
# builder   linux     debian        builder/linux/debian/   $VERSION
# builder   linux     rhel          builder/linux/rhel/     $VERSION

while read product platform type key; do
  until [[ $(jobs -lr | wc -l) -lt $JOBS ]]; do
    sleep 1
  done
  {
    install -D -m 644 /dev/null $S3_TEMP_DIR/$key
    aws s3api head-object --bucket $S3_BUCKET --key $key > $S3_TEMP_DIR/$key
  } &
done < $list
wait

if [[ "${JSON-}" -eq 1 ]]; then

  msg
  msg "${BOLD}JSON:${NOFORMAT}"

  while read product platform type key; do
    msg "$GREEN$product > $platform > $type > $key$NOFORMAT"
    obj=$(jq -c '.' $S3_TEMP_DIR/$key)
    size=$(<<<$obj jq -er '.ContentLength' || echo -n "0")
    sha256=$(<<<$obj jq -er '.Metadata.sha256' ||:)
    sha1=$(<<<$obj jq -er '.Metadata.sha1' ||:)
    md5=$(<<<$obj jq -er '.Metadata.md5' ||:)
    jq ".$product.$platform.$type += [{
      key: \"$key\",
      size: $size,
      sha256: \"$sha256\",
      sha1: \"$sha1\",
      md5: \"$md5\"
    }]" $json \
      > $json.tmp && mv -f $json.tmp $json
  done < $list

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
  [cm_sdkjs_oss]="SDKJS Closure Maps Open Source"
  [cm_sdkjs_com]="SDKJS Closure Maps Commercial"
  [cm_webapps]="WEB-APPS Closure Maps"
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
  [python]="Python"
)

if [[ "${HTML-}" -eq 1 ]]; then

  msg
  msg "${BOLD}HTML:${NOFORMAT}"

  [[ ! -f $json ]] && die "Artifacts not found" 0
  jq -r "keys_unsorted[]" $json | while read product; do

    msg "$BLUE$product$NOFORMAT"
    html=$REPORTS_DIR/$product.html

    cat << EOF > $html
<!DOCTYPE html>
<html lang="en-US" data-color-mode="auto" data-light-theme="light" data-dark-theme="dark">
<head>
  <title>$COMPANY_NAME ${PRODUCT_TITLES[$product]} - $BRANCH_NAME - $BUILD_NUMBER</title>
  <link rel="shortcut icon" href="$S3_BASE_URL/favicon.ico" type="image/x-icon">
  <link rel="stylesheet" href="https://unpkg.com/@primer/css@21.0.7/dist/primer.css">
</head>
<body>
  <div class="container-lg px-3 my-5 markdown-body">
  <h1>$COMPANY_NAME ${PRODUCT_TITLES[$product]} - $BRANCH_NAME - $BUILD_NUMBER</h1>
  <p class="color-fg-muted">$DATE_NOW</p>
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
            echo "    <summary>" >> $html
            echo "      <a href=\"$S3_BASE_URL/$key\">${key##*/}</a>" >> $html
            echo "      $(LANG=C numfmt --to=iec-i $size)B" >> $html
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
    echo -n "/$product.html\" target=\"_blank\">$product</a>" >> $desc

  done

  if [[ "${DEPLOY-}" -eq 1 ]]; then
    msg
    msg "${BOLD}Upload:${NOFORMAT}"

    aws s3 sync --no-progress \
      $REPORTS_DIR \
      s3://$S3_BUCKET/reports/$BRANCH_NAME/$BUILD_NUMBER
    aws s3 sync --no-progress --delete \
      s3://$S3_BUCKET/reports/$BRANCH_NAME/$BUILD_NUMBER \
      s3://$S3_BUCKET/reports/$BRANCH_NAME/latest

    find $REPORTS_DIR -type f -printf \
      "URL: $S3_BASE_URL/reports/$BRANCH_NAME/$BUILD_NUMBER/%f\n"
    find $REPORTS_DIR -type f -printf \
      "URL: $S3_BASE_URL/reports/$BRANCH_NAME/latest/%f\n"
  fi

fi
