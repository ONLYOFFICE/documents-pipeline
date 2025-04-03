#!/usr/bin/env bash

set -Eeuxo pipefail
cd "${0%/*}"

parse_params() {
  : "${COMPANY_NAME:=ONLYOFFICE}"
  : "${S3_BUCKET:=repo-doc-onlyoffice-com}"
  : "${S3_BASE_URL:=https://s3.eu-west-1.amazonaws.com/repo-doc-onlyoffice-com}"
  GITHUB_BASE_URL="https://github.com/ONLYOFFICE/4testing-DesktopEditors/releases/download"
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

  DESKTOP_NAME="$COMPANY_NAME Desktop Editors"
  PACKAGE_NAME="$COMPANY_NAME-DesktopEditors"
  CHANGES_URL="$S3_BASE_URL/desktop/win/update/$BUILD_VERSION/$BUILD_NUMBER"

  UPD_64_KEY="desktop/win/inno/$PACKAGE_NAME-Update-$BUILD_VERSION.$BUILD_NUMBER-x64.exe"
  UPD_32_KEY="desktop/win/inno/$PACKAGE_NAME-Update-$BUILD_VERSION.$BUILD_NUMBER-x86.exe"
  ZIP_64_KEY="desktop/win/generic/$PACKAGE_NAME-$BUILD_VERSION.$BUILD_NUMBER-x64.zip"
  ZIP_32_KEY="desktop/win/generic/$PACKAGE_NAME-$BUILD_VERSION.$BUILD_NUMBER-x86.zip"
  EXE_64_KEY="desktop/win/inno/$PACKAGE_NAME-$BUILD_VERSION.$BUILD_NUMBER-x64.exe"
  EXE_32_KEY="desktop/win/inno/$PACKAGE_NAME-$BUILD_VERSION.$BUILD_NUMBER-x86.exe"
  MSI_64_KEY="desktop/win/advinst/$PACKAGE_NAME-$BUILD_VERSION.$BUILD_NUMBER-x64.msi"
  MSI_32_KEY="desktop/win/advinst/$PACKAGE_NAME-$BUILD_VERSION.$BUILD_NUMBER-x86.msi"

  ZIP_64_GH="v$BUILD_VERSION-$BUILD_NUMBER/$PACKAGE_NAME-x64.zip"
  ZIP_32_GH="v$BUILD_VERSION-$BUILD_NUMBER/$PACKAGE_NAME-x86.zip"

  DATE_JSON=$(LANG=C TZ=UTC date -u "+%b %d %H:%M UTC %Y")
  DATE_XML=$(LANG=C TZ=UTC date -u "+%a, %d %b %Y %H:%M:%S +0000")

  return 0
}

parse_params "$@"

rm -rfv $UPDATE_DIR $S3_TEMP_DIR
mkdir -pv $UPDATE_DIR

# APPCAST JSON
for key in \
  $ZIP_64_KEY $EXE_64_KEY $MSI_64_KEY \
  $ZIP_32_KEY $EXE_32_KEY $MSI_32_KEY
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
      "url": "$S3_BASE_URL/$UPD_64_KEY",
      "installArguments": "/silent /update",
      "archive": {
        "url": "$GITHUB_BASE_URL/$ZIP_64_GH",
        "url2": "$S3_BASE_URL/$ZIP_64_KEY",
        "md5": "$(s3_md5 $ZIP_64_KEY)"
      },
      "iss": {
        "url": "$S3_BASE_URL/$EXE_64_KEY",
        "url2": "$S3_BASE_URL/$EXE_64_KEY",
        "md5": "$(s3_md5 $EXE_64_KEY)",
        "arguments": "/silent /update",
        "maxVersion": "7.3.3"
      },
      "msi": {
        "url": "$S3_BASE_URL/$MSI_64_KEY",
        "url2": "$S3_BASE_URL/$MSI_64_KEY",
        "md5": "$(s3_md5 $MSI_64_KEY)",
        "arguments": "/qr /norestart",
        "maxVersion": "7.5.0"
      }
    },
    "win_32": {
      "url": "$S3_BASE_URL/$UPD_32_KEY",
      "installArguments": "/silent /update",
      "archive": {
        "url": "$GITHUB_BASE_URL/$ZIP_32_GH",
        "url2": "$S3_BASE_URL/$ZIP_32_KEY",
        "md5": "$(s3_md5 $ZIP_32_KEY)"
      },
      "iss": {
        "url": "$S3_BASE_URL/$EXE_32_KEY",
        "url2": "$S3_BASE_URL/$EXE_32_KEY",
        "md5": "$(s3_md5 $EXE_32_KEY)",
        "arguments": "/silent /update",
        "maxVersion": "7.3.3"
      },
      "msi": {
        "url": "$S3_BASE_URL/$MSI_32_KEY",
        "url2": "$S3_BASE_URL/$MSI_32_KEY",
        "md5": "$(s3_md5 $MSI_32_KEY)",
        "arguments": "/qr /norestart",
        "maxVersion": "7.5.0"
      }
    }
  }
}
EOF

# APPCAST XML
tee $UPDATE_DIR/appcast.xml << EOF
<?xml version="1.0" encoding="UTF-8"?>
<rss xmlns:sparkle="http://www.andymatuschak.org/xml-namespaces/sparkle" xmlns:dc="http://purl.org/dc/elements/1.1/" version="2.0">
  <channel>
    <title>$DESKTOP_NAME Changelog</title>
    <description>Most recent changes with links to updates.</description>
    <language>en</language>
    <item>
      <title>Version $BUILD_VERSION.$BUILD_NUMBER</title>
      <pubDate>$DATE_XML</pubDate>
      <sparkle:releaseNotesLink>$CHANGES_URL/changes.html</sparkle:releaseNotesLink>
      <sparkle:releaseNotesLink xml:lang="ru-RU">$CHANGES_URL/changes_ru.html</sparkle:releaseNotesLink>
      <enclosure url="$S3_BASE_URL/$UPD_64_KEY" sparkle:os="windows-x64" sparkle:version="$BUILD_VERSION.$BUILD_NUMBER" sparkle:shortVersionString="$BUILD_VERSION.$BUILD_NUMBER" sparkle:installerArguments="/silent /update" length="0" type="application/octet-stream"/>
    </item>
    <item>
      <title>Version $BUILD_VERSION.$BUILD_NUMBER</title>
      <pubDate>$DATE_XML</pubDate>
      <sparkle:releaseNotesLink>$CHANGES_URL/changes.html</sparkle:releaseNotesLink>
      <sparkle:releaseNotesLink xml:lang="ru-RU">$CHANGES_URL/changes_ru.html</sparkle:releaseNotesLink>
      <enclosure url="$S3_BASE_URL/$UPD_32_KEY" sparkle:os="windows-x86" sparkle:version="$BUILD_VERSION.$BUILD_NUMBER" sparkle:shortVersionString="$BUILD_VERSION.$BUILD_NUMBER" sparkle:installerArguments="/silent /update" length="0" type="application/octet-stream"/>
    </item>
  </channel>
</rss>
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
