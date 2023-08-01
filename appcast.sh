#!/usr/bin/env bash

set -Eeuo pipefail
cd "${0%/*}"

s3_md5() {
  $aws s3api head-object --bucket $S3_BUCKET --key $1 \
    --query 'Metadata.md5' --output text \
    2> /dev/null || true
}

set -x

# PRODUCT_VERSION=0.0.0
# BUILD_NUMBER=0
# S3_BUCKET=repo-doc-onlyoffice-com
# S3_BASE_URL=https://s3.eu-west-1.amazonaws.com/repo-doc-onlyoffice-com

PACKAGE_NAME="ONLYOFFICE-DesktopEditors"
DATE_JSON=$(LANG=C date -u "+%b %d %H:%M UTC %Y")
DATE_XML=$(LANG=C date -u "+%a, %d %b %Y %H:%M:%S +0000")
UPDATES_URL="$S3_BASE_URL/desktop/win/inno/$PRODUCT_VERSION/$BUILD_NUMBER"
CHANGES_URL="$S3_BASE_URL/desktop/win/update/$PRODUCT_VERSION/$BUILD_NUMBER"
ZIP_64_KEY="desktop/win/generic/$PACKAGE_NAME-$PRODUCT_VERSION.$BUILD_NUMBER-x64.zip"
ZIP_32_KEY="desktop/win/generic/$PACKAGE_NAME-$PRODUCT_VERSION.$BUILD_NUMBER-x86.zip"
EXE_64_KEY="desktop/win/inno/$PACKAGE_NAME-$PRODUCT_VERSION.$BUILD_NUMBER-x64.exe"
EXE_32_KEY="desktop/win/inno/$PACKAGE_NAME-$PRODUCT_VERSION.$BUILD_NUMBER-x86.exe"
MSI_64_KEY="desktop/win/advinst/$PACKAGE_NAME-$PRODUCT_VERSION.$BUILD_NUMBER-x64.msi"
MSI_32_KEY="desktop/win/advinst/$PACKAGE_NAME-$PRODUCT_VERSION.$BUILD_NUMBER-x86.msi"
aws="aws"

rm -rfv update repo
mkdir -pv update

echo "Make appcast"

cat > update/appcast.json << EOF
{
  "version": "$PRODUCT_VERSION.$BUILD_NUMBER",
  "date": "$DATE_JSON",
  "releaseNotes": {
    "en-EN": "$CHANGES_URL/changes.html",
    "ru-RU": "$CHANGES_URL/changes_ru.html"
  },
  "package": {
    "win_64": {
      "url": "$UPDATES_URL/editors_update_x64.exe",
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
      "url": "$UPDATES_URL/editors_update_x86.exe",
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
cat update/appcast.json

cat > update/appcast.xml << EOF
<?xml version="1.0" encoding="UTF-8"?>
<rss xmlns:sparkle="http://www.andymatuschak.org/xml-namespaces/sparkle" xmlns:dc="http://purl.org/dc/elements/1.1/" version="2.0">
  <channel>
    <title>$PACKAGE_NAME Changelog</title>
    <description>Most recent changes with links to updates.</description>
    <language>en</language>
    <item>
      <title>Version $PRODUCT_VERSION.$BUILD_NUMBER</title>
      <pubDate>$DATE_XML</pubDate>
      <sparkle:releaseNotesLink>$CHANGES_URL/changes.html</sparkle:releaseNotesLink>
      <sparkle:releaseNotesLink xml:lang="ru-RU">$CHANGES_URL/changes_ru.html</sparkle:releaseNotesLink>
      <enclosure url="$UPDATES_URL/editors_update_x64.exe" sparkle:os="windows-x64" sparkle:version="$PRODUCT_VERSION.$BUILD_NUMBER" sparkle:shortVersionString="$PRODUCT_VERSION.$BUILD_NUMBER" sparkle:installerArguments="/silent /update" length="0" type="application/octet-stream"/>
    </item>
    <item>
      <title>Version $PRODUCT_VERSION.$BUILD_NUMBER</title>
      <pubDate>$DATE_XML</pubDate>
      <sparkle:releaseNotesLink>$CHANGES_URL/changes.html</sparkle:releaseNotesLink>
      <sparkle:releaseNotesLink xml:lang="ru-RU">$CHANGES_URL/changes_ru.html</sparkle:releaseNotesLink>
      <enclosure url="$UPDATES_URL/editors_update_x86.exe" sparkle:os="windows-x86" sparkle:version="$PRODUCT_VERSION.$BUILD_NUMBER" sparkle:shortVersionString="$PRODUCT_VERSION.$BUILD_NUMBER" sparkle:installerArguments="/silent /update" length="0" type="application/octet-stream"/>
    </item>
  </channel>
</rss>
EOF
cat update/appcast.xml

echo "Upload"
$aws s3 sync --no-progress --acl public-read update \
  s3://$S3_BUCKET/desktop/win/update/$PRODUCT_VERSION/$BUILD_NUMBER

echo "Make deploy.json"
cat > deploy.json << EOF
[
  {
    "key": "desktop/win/update/$PRODUCT_VERSION/$BUILD_NUMBER/appcast.json",
    "platform": "Windows x64",
    "product": "desktop",
    "size": $(stat -c %s update/appcast.json || echo 0),
    "type": "Update"
  },
  {
    "key": "desktop/win/update/$PRODUCT_VERSION/$BUILD_NUMBER/appcast.xml",
    "platform": "Windows x64",
    "product": "desktop",
    "size": $(stat -c %s update/appcast.xml || echo 0),
    "type": "Update"
  }
]
EOF
