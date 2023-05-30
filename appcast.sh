#!/usr/bin/env bash

set -Eeuo pipefail
cd "${0%/*}"

usage() {
  cat <<EOF
Usage: $(basename "${BASH_SOURCE[0]}") [-h] [-v] -r VERSION -b BUILD

Available options:

-h, --help                Print this help and exit
-v, --verbose             Print script debug info
-r, --version             0.0.0
-b, --build               0
EOF
  exit
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
  while :; do
    case "${1-}" in
      -h | --help) usage ;;
      -v | --verbose) set -x ;;
      -a | --version) VERSION="${2-}"; shift;;
      -b | --build) BUILD="${2-}"; shift;;
      -r | --branch) BRANCH_NAME="${2-}"; shift;;
      -?*) die "Unknown option: $1" ;;
      *) break ;;
    esac
    shift
  done

  [[ -z "${VERSION-}"     ]] && die "Missing required parameter: VERSION"
  [[ -z "${BUILD-}"       ]] && die "Missing required parameter: BUILD"
  [[ -z "${BRANCH_NAME-}" ]] && die "Missing required parameter: BRANCH_NAME"
  return 0
}

s3_md5() {
  aws s3api head-object --bucket $S3_BUCKET --key $1 --query 'Metadata.md5' --output text \
    2> /dev/null || true
}

parse_params "$@"

S3_BUCKET=repo-doc-onlyoffice-com
COMPANY_NAME=ONLYOFFICE
PRODUCT_NAME=DesktopEditors
DATE=$(LANG=C date -u "+%b %d %H:%M UTC %Y")
aws="aws"

rm -rfv update
mkdir -pv update

msg "Download changelog"
pushd update
wget -nv https://raw.githubusercontent.com/ONLYOFFICE/desktop-apps/$BRANCH_NAME/win-linux/package/windows/update/changes/$VERSION/changes.html
wget -nv https://raw.githubusercontent.com/ONLYOFFICE/desktop-apps/$BRANCH_NAME/win-linux/package/windows/update/changes/$VERSION/changes_ru.html
popd

msg "Make appcast"
BASE_URL=https://s3.eu-west-1.amazonaws.com/repo-doc-onlyoffice-com
UPDATES_URL=$BASE_URL/desktop/win/update/$VERSION/$BUILD
CHANGES_URL=$BASE_URL/desktop/win/update/$VERSION/$BUILD
ZIP_64_KEY=desktop/win/generic/$COMPANY_NAME-$PRODUCT_NAME-$VERSION.$BUILD-x64.zip
ZIP_32_KEY=desktop/win/generic/$COMPANY_NAME-$PRODUCT_NAME-$VERSION.$BUILD-x86.zip
EXE_64_KEY=desktop/win/inno/$COMPANY_NAME-$PRODUCT_NAME-$VERSION.$BUILD-x64.exe
EXE_32_KEY=desktop/win/inno/$COMPANY_NAME-$PRODUCT_NAME-$VERSION.$BUILD-x86.exe
MSI_64_KEY=desktop/win/advinst/$COMPANY_NAME-$PRODUCT_NAME-$VERSION.$BUILD-x64.msi
MSI_32_KEY=desktop/win/advinst/$COMPANY_NAME-$PRODUCT_NAME-$VERSION.$BUILD-x86.msi
cat > update/appcast.json << EOF
{
  "version": "$VERSION.$BUILD",
  "date": "$DATE",
  "releaseNotes": {
    "en-EN": "$CHANGES_URL/changes.html",
    "ru-RU": "$CHANGES_URL/changes_ru.html"
  },
  "package": {
    "win_64": {
      "url": "$UPDATES_URL/editors_update_x64.exe",
      "installArguments": "/silent /update",
      "archive": {
        "url": "$BASE_URL/$ZIP_64_KEY",
        "md5": "$(s3_md5 $ZIP_64_KEY)"
      },
      "iss": {
        "url": "$BASE_URL/$EXE_64_KEY",
        "md5": "$(s3_md5 $EXE_64_KEY)",
        "installArguments": "/silent /update",
        "maxVersion": "7.3.3"
      },
      "msi": {
        "url": "$BASE_URL/$MSI_64_KEY",
        "md5": "$(s3_md5 $MSI_64_KEY)",
        "installArguments": "/qn /norestart",
        "maxVersion": "7.3.3"
      }
    },
    "win_32": {
      "url": "$UPDATES_URL/editors_update_x86.exe",
      "installArguments": "/silent /update",
      "archive": {
        "url": "$BASE_URL/$ZIP_32_KEY",
        "md5": "$(s3_md5 $ZIP_32_KEY)"
      },
      "iss": {
        "url": "$BASE_URL/$EXE_32_KEY",
        "md5": "$(s3_md5 $EXE_32_KEY)",
        "installArguments": "/silent /update",
        "maxVersion": "7.3.3"
      },
      "msi": {
        "url": "$BASE_URL/$MSI_32_KEY",
        "md5": "$(s3_md5 $MSI_32_KEY)",
        "installArguments": "/qn /norestart",
        "maxVersion": "7.3.3"
      }
    }
  }
}
EOF

msg "Upload"
aws s3 sync --no-progress --acl public-read update \
  s3://$S3_BUCKET/desktop/win/update/$VERSION/$BUILD

msg "Make deploy.json"
cat > deploy.json << EOF
[
  {
    "key": "desktop/win/update/$VERSION/$BUILD/appcast.json",
    "platform": "Windows x64",
    "product": "desktop",
    "size": $(stat -c %s update/appcast.json || echo 0),
    "type": "Update"
  },
  {
    "key": "desktop/win/update/$VERSION/$BUILD/changes.html",
    "platform": "Windows x64",
    "product": "desktop",
    "size": $(stat -c %s update/changes.json || echo 0),
    "type": "Update"
  },
  {
    "key": "desktop/win/update/$VERSION/$BUILD/changes_ru.html",
    "platform": "Windows x64",
    "product": "desktop",
    "size": $(stat -c %s update/changes_ru.json || echo 0),
    "type": "Update"
  }
]
EOF
