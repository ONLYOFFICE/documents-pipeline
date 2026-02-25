#!/usr/bin/env bash

set -Eeuxo pipefail
cd "${0%/*}"

parse_params() {
  : "${S3_BUCKET:=repo-doc-onlyoffice-com}"
  : "${S3_BASE_URL:=https://s3.eu-west-1.amazonaws.com/repo-doc-onlyoffice-com}"
  UPDATE_DIR=update
  S3_TEMP_DIR=s3
  DEPLOY=0
  JOBS=8

  while :; do
    case "${1-}" in
      -v | --version )
        BUILD_VERSION="${2-}"
        shift
        ;;
      -n | --number )
        BUILD_NUMBER="${2-}"
        shift
        ;;
      -d | --deploy )
        DEPLOY=1
        ;;
      -j | --jobs )
        JOBS="${2-}"
        shift
        ;;
      -?* )
        echo "Unknown option: $1" >&2
        ;;
      * )
        break
        ;;
    esac
    shift
  done

  DESKTOP_NAME="ONLYOFFICE Desktop Editors"
  PACKAGE_NAME="ONLYOFFICE-DesktopEditors"
  PACKAGE_VER_NAME="$PACKAGE_NAME-$BUILD_VERSION.$BUILD_NUMBER"
  CHANGES_URL="$S3_BASE_URL/desktop/win/update/$BUILD_VERSION/$BUILD_NUMBER"
  GITHUB_BASE_URL="https://github.com/ONLYOFFICE/4testing-DesktopEditors/releases/download/v$BUILD_VERSION-$BUILD_NUMBER"

  ZIP_X64_KEY="desktop/win/generic/$PACKAGE_VER_NAME-x64.zip"
  ZIP_X86_KEY="desktop/win/generic/$PACKAGE_VER_NAME-x86.zip"
  ZIP_ARM64_KEY="desktop/win/generic/$PACKAGE_VER_NAME-arm64.zip"
  EXE_X64_KEY="desktop/win/inno/$PACKAGE_VER_NAME-x64.exe"
  EXE_X86_KEY="desktop/win/inno/$PACKAGE_VER_NAME-x86.exe"
  EXE_ARM64_KEY="desktop/win/inno/$PACKAGE_VER_NAME-arm64.exe"
  MSI_X64_KEY="desktop/win/advinst/$PACKAGE_VER_NAME-x64.msi"
  MSI_X86_KEY="desktop/win/advinst/$PACKAGE_VER_NAME-x86.msi"
  MSI_ARM64_KEY="desktop/win/advinst/$PACKAGE_VER_NAME-arm64.msi"

  DATE_JSON=$(LANG=C TZ=UTC date -u "+%b %d %H:%M UTC %Y")

  return 0
}

parse_params "$@"

rm -rfv $UPDATE_DIR $S3_TEMP_DIR
mkdir -pv $UPDATE_DIR

# APPCAST JSON
for key in \
  $ZIP_X64_KEY $EXE_X64_KEY $MSI_X64_KEY \
  $ZIP_X86_KEY $EXE_X86_KEY $MSI_X86_KEY \
  $ZIP_ARM64_KEY $EXE_ARM64_KEY $MSI_ARM64_KEY
do
  until [[ $(jobs -lr | wc -l) -lt $JOBS ]]; do
    sleep 1
  done
  {
    install -D -m 644 /dev/null $S3_TEMP_DIR/$key
    aws s3api head-object --bucket $S3_BUCKET --key $key > $S3_TEMP_DIR/$key
  } &
done
wait

s3_md5() {
  jq -r '.Metadata.md5 // empty' $S3_TEMP_DIR/$1
}

tee $UPDATE_DIR/appcast.json << EOF
{
  "version": "$BUILD_VERSION.$BUILD_NUMBER",
  "date": "$DATE_JSON",
  "releaseNotes": {
    "en-EN": "$CHANGES_URL/changes.html",
    "ru-RU": "$CHANGES_URL/changes_ru.html"
  },
  "package": {
    "win_64": {
      "url": "https://download.onlyoffice.com/install/desktop/editors/windows/onlyoffice/updates/editors_update_x64.exe",
      "installArguments": "/silent /update",
      "archive": {
        "url": "$GITHUB_BASE_URL/$PACKAGE_NAME-x64.zip",
        "url2": "$S3_BASE_URL/$ZIP_X64_KEY",
        "md5": "$(s3_md5 $ZIP_X64_KEY)"
      },
      "iss": {
        "url": "$GITHUB_BASE_URL/$PACKAGE_NAME-x64.exe",
        "url2": "$S3_BASE_URL/$EXE_X64_KEY",
        "md5": "$(s3_md5 $EXE_X64_KEY)",
        "arguments": "/silent /update",
        "maxVersion": "7.3.3"
      },
      "msi": {
        "url": "$GITHUB_BASE_URL/$PACKAGE_NAME-x64.msi",
        "url2": "$S3_BASE_URL/$MSI_X64_KEY",
        "md5": "$(s3_md5 $MSI_X64_KEY)",
        "arguments": "/qr /norestart",
        "maxVersion": "7.5.0"
      }
    },
    "win_32": {
      "url": "https://download.onlyoffice.com/install/desktop/editors/windows/onlyoffice/updates/editors_update_x86.exe",
      "installArguments": "/silent /update",
      "archive": {
        "url": "$GITHUB_BASE_URL/$PACKAGE_NAME-x86.zip",
        "url2": "$S3_BASE_URL/$ZIP_X86_KEY",
        "md5": "$(s3_md5 $ZIP_X86_KEY)"
      },
      "iss": {
        "url": "$GITHUB_BASE_URL/$PACKAGE_NAME-x86.exe",
        "url2": "$S3_BASE_URL/$EXE_X86_KEY",
        "md5": "$(s3_md5 $EXE_X86_KEY)",
        "arguments": "/silent /update",
        "maxVersion": "7.3.3"
      },
      "msi": {
        "url": "$GITHUB_BASE_URL/$PACKAGE_NAME-x86.msi",
        "url2": "$S3_BASE_URL/$MSI_X86_KEY",
        "md5": "$(s3_md5 $MSI_X86_KEY)",
        "arguments": "/qr /norestart",
        "maxVersion": "7.5.0"
      }
    },
    "win_arm64": {
      "installArguments": "/silent /update",
      "archive": {
        "url": "$GITHUB_BASE_URL/$PACKAGE_NAME-arm64.zip",
        "url2": "$S3_BASE_URL/$ZIP_ARM64_KEY",
        "md5": "$(s3_md5 $ZIP_ARM64_KEY)"
      },
      "iss": {
        "url": "$GITHUB_BASE_URL/$PACKAGE_NAME-arm64.exe",
        "url2": "$S3_BASE_URL/$EXE_ARM64_KEY",
        "md5": "$(s3_md5 $EXE_ARM64_KEY)",
        "arguments": "/silent /update",
        "maxVersion": "7.3.3"
      },
      "msi": {
        "url": "$GITHUB_BASE_URL/$PACKAGE_NAME-arm64.msi",
        "url2": "$S3_BASE_URL/$MSI_ARM64_KEY",
        "md5": "$(s3_md5 $MSI_ARM64_KEY)",
        "arguments": "/qr /norestart",
        "maxVersion": "7.5.0"
      }
    }
  }
}
EOF

# APPCAST CHANGES
for c in changes changes_ru; do
  case $c in
    changes )
      HTML_TITLE="%s Release Notes"
      HTML_MORE="See list of the changes"
      ;;
    changes_ru )
      HTML_TITLE="История изменений %s"
      HTML_MORE="Список изменений"
      ;;
  esac
  tee $UPDATE_DIR/$c.html << EOF
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>${HTML_TITLE//%s/$DESKTOP_NAME}</title>
  <style type="text/css">
    body {
      background: white;
      font-size: 12px;
      font-family: sans-serif;
    }
    h1, h2 {
      font-weight: normal;
      font-style: normal;
    }
    h1 {
      font-size: 18px;
    }
    h2 {
      font-size: 16px;
    }
    a {
      text-decoration: none;
    }
    .releasedate {
        color: #888;
        font-size: medium;
    }
  </style>
</head>
<body>
  <h1>ONLYOFFICE Desktop Editors $BUILD_VERSION<span class="releasedate"> - {{DATE}}</span></h1>
  <h2><a href="https://github.com/ONLYOFFICE/DesktopEditors/blob/master/CHANGELOG.md#${BUILD_VERSION//./}" target="_blank">$HTML_MORE</a></h2>
</body>
</html>
EOF
done

# UPLOAD
if [[ "${DEPLOY-}" -eq 1 ]]; then
  for f in $UPDATE_DIR/*; do
    until [[ $(jobs -lr | wc -l) -lt $JOBS ]]; do
      sleep 1
    done
    {
      sha256=$(sha256sum $f | cut -d' ' -f1)
      sha1=$(sha1sum $f | cut -d' ' -f1)
      md5=$(md5sum $f | cut -d' ' -f1)
      aws s3 cp --no-progress --metadata sha256=$sha256,sha1=$sha1,md5=$md5 \
        $f s3://$S3_BUCKET/desktop/win/update/$BUILD_VERSION/$BUILD_NUMBER/
    } &
  done
  wait
  find $UPDATE_DIR -type f -printf \
    "URL: $S3_BASE_URL/desktop/win/update/$BUILD_VERSION/$BUILD_NUMBER/%f\n"
fi
