#!/usr/bin/env bash

set -Eeuo pipefail
cd "${0%/*}"

s3_md5() {
  aws s3api head-object --bucket $S3_BUCKET --key $1 \
    --query 'Metadata.md5' --output text \
    2> /dev/null || echo -n 0
}

set -x

PACKAGE_NAME="ONLYOFFICE-DesktopEditors"
DATE_JSON=$(LANG=C TZ=UTC date -u "+%b %d %H:%M UTC %Y")
DATE_XML=$(LANG=C TZ=UTC date -u "+%a, %d %b %Y %H:%M:%S +0000")
CHANGES_URL="$S3_BASE_URL/desktop/win/update/$BUILD_VERSION/$BUILD_NUMBER"
EXEUPD_64_KEY="desktop/win/inno/$PACKAGE_NAME-Update-$BUILD_VERSION.$BUILD_NUMBER-x64.exe"
EXEUPD_32_KEY="desktop/win/inno/$PACKAGE_NAME-Update-$BUILD_VERSION.$BUILD_NUMBER-x86.exe"
ZIP_64_KEY="desktop/win/generic/$PACKAGE_NAME-$BUILD_VERSION.$BUILD_NUMBER-x64.zip"
ZIP_32_KEY="desktop/win/generic/$PACKAGE_NAME-$BUILD_VERSION.$BUILD_NUMBER-x86.zip"
EXE_64_KEY="desktop/win/inno/$PACKAGE_NAME-$BUILD_VERSION.$BUILD_NUMBER-x64.exe"
EXE_32_KEY="desktop/win/inno/$PACKAGE_NAME-$BUILD_VERSION.$BUILD_NUMBER-x86.exe"
MSI_64_KEY="desktop/win/advinst/$PACKAGE_NAME-$BUILD_VERSION.$BUILD_NUMBER-x64.msi"
MSI_32_KEY="desktop/win/advinst/$PACKAGE_NAME-$BUILD_VERSION.$BUILD_NUMBER-x86.msi"
appc_j=update/appcast.json
appc_x=update/appcast.xml
keys_t=deploy.txt

rm -rfv update $keys_t
mkdir -pv update

echo "MAKE APPCAST"

tee $appc_j << EOF
{
  "version": "$BUILD_VERSION.$BUILD_NUMBER",
  "date": "$DATE_JSON",
  "releaseNotes": {
    "en-EN": "$CHANGES_URL/changes.html",
    "ru-RU": "$CHANGES_URL/changes_ru.html"
  },
  "package": {
    "win_64": {
      "url": "$S3_BASE_URL/$EXEUPD_64_KEY",
      "installArguments": "/silent /update",
      "archive": {
        "url": "$S3_BASE_URL/$ZIP_64_KEY",
        "md5": "$(s3_md5 $ZIP_64_KEY)"
      },
      "iss": {
        "url": "$S3_BASE_URL/$EXE_64_KEY",
        "md5": "$(s3_md5 $EXE_64_KEY)",
        "arguments": "/silent /update",
        "maxVersion": "7.3.3"
      },
      "msi": {
        "url": "$S3_BASE_URL/$MSI_64_KEY",
        "md5": "$(s3_md5 $MSI_64_KEY)",
        "arguments": "/qr /norestart",
        "maxVersion": "7.5.0"
      }
    },
    "win_32": {
      "url": "$S3_BASE_URL/$EXEUPD_32_KEY",
      "installArguments": "/silent /update",
      "archive": {
        "url": "$S3_BASE_URL/$ZIP_32_KEY",
        "md5": "$(s3_md5 $ZIP_32_KEY)"
      },
      "iss": {
        "url": "$S3_BASE_URL/$EXE_32_KEY",
        "md5": "$(s3_md5 $EXE_32_KEY)",
        "arguments": "/silent /update",
        "maxVersion": "7.3.3"
      },
      "msi": {
        "url": "$S3_BASE_URL/$MSI_32_KEY",
        "md5": "$(s3_md5 $MSI_32_KEY)",
        "arguments": "/qr /norestart",
        "maxVersion": "7.5.0"
      }
    }
  }
}
EOF

tee $appc_x << EOF
<?xml version="1.0" encoding="UTF-8"?>
<rss xmlns:sparkle="http://www.andymatuschak.org/xml-namespaces/sparkle" xmlns:dc="http://purl.org/dc/elements/1.1/" version="2.0">
  <channel>
    <title>$PACKAGE_NAME Changelog</title>
    <description>Most recent changes with links to updates.</description>
    <language>en</language>
    <item>
      <title>Version $BUILD_VERSION.$BUILD_NUMBER</title>
      <pubDate>$DATE_XML</pubDate>
      <sparkle:releaseNotesLink>$CHANGES_URL/changes.html</sparkle:releaseNotesLink>
      <sparkle:releaseNotesLink xml:lang="ru-RU">$CHANGES_URL/changes_ru.html</sparkle:releaseNotesLink>
      <enclosure url="$S3_BASE_URL/$EXEUPD_64_KEY" sparkle:os="windows-x64" sparkle:version="$BUILD_VERSION.$BUILD_NUMBER" sparkle:shortVersionString="$BUILD_VERSION.$BUILD_NUMBER" sparkle:installerArguments="/silent /update" length="0" type="application/octet-stream"/>
    </item>
    <item>
      <title>Version $BUILD_VERSION.$BUILD_NUMBER</title>
      <pubDate>$DATE_XML</pubDate>
      <sparkle:releaseNotesLink>$CHANGES_URL/changes.html</sparkle:releaseNotesLink>
      <sparkle:releaseNotesLink xml:lang="ru-RU">$CHANGES_URL/changes_ru.html</sparkle:releaseNotesLink>
      <enclosure url="$S3_BASE_URL/$EXEUPD_32_KEY" sparkle:os="windows-x86" sparkle:version="$BUILD_VERSION.$BUILD_NUMBER" sparkle:shortVersionString="$BUILD_VERSION.$BUILD_NUMBER" sparkle:installerArguments="/silent /update" length="0" type="application/octet-stream"/>
    </item>
  </channel>
</rss>
EOF

echo "UPLOAD"

for f in update/*; do
  md5sum=$(md5sum $f | cut -d' ' -f1)
  aws s3 cp --no-progress --acl public-read --metadata md5=$md5sum \
    $f s3://$S3_BUCKET/desktop/win/update/$BUILD_VERSION/$BUILD_NUMBER/
  echo "URL: $S3_BASE_URL/desktop/win/update/$BUILD_VERSION/$BUILD_NUMBER/${f##*/}"
  echo "desktop/win/update/$BUILD_VERSION/$BUILD_NUMBER/${f##*/}" >> $keys_t
done
