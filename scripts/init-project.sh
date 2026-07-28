#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")/.."

PROJECT_SLUG="${1:-}"
PACKAGE_NAME="${2:-}"
DISPLAY_NAME="${3:-}"

CURRENT_PACKAGE="com.backendsystemdesignlab.template"
CURRENT_PACKAGE_PATH="com/backendsystemdesignlab/template"
CURRENT_APPLICATION_CLASS="SystemDesignTemplateApplication"

if [[ -z "$PROJECT_SLUG" || -z "$PACKAGE_NAME" || -z "$DISPLAY_NAME" ]]; then
  echo "사용법:"
  echo "./scripts/init-project.sh <project-slug> <package-name> <display-name>"
  echo
  echo "예시:"
  echo "./scripts/init-project.sh \\"
  echo "  url-shortener \\"
  echo "  com.backendsystemdesignlab.urlshortener \\"
  echo '  "URL Shortener"'
  exit 1
fi

if [[ ! "$PROJECT_SLUG" =~ ^[a-z0-9]+([_-][a-z0-9]+)*$ ]]; then
  echo "프로젝트 식별자는 소문자, 숫자, 하이픈, 언더스코어만 사용할 수 있습니다."
  exit 1
fi

if [[ ! "$PACKAGE_NAME" =~ ^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$ ]]; then
  echo "올바르지 않은 Java 패키지명입니다: $PACKAGE_NAME"
  exit 1
fi

if [[ -f ".template-initialized" ]]; then
  echo "이미 초기화된 프로젝트입니다."
  cat .template-initialized
  exit 1
fi

if [[ ! -d "src/main/java/$CURRENT_PACKAGE_PATH" ]]; then
  echo "기본 템플릿 패키지를 찾을 수 없습니다."
  echo "현재 Repository가 원본 System Design Template인지 확인하세요."
  exit 1
fi

# url-shortener -> UrlShortener
APPLICATION_PREFIX="$(
  echo "$PROJECT_SLUG" |
    tr '_-' '  ' |
    awk '{
      for (i = 1; i <= NF; i++) {
        printf "%s", toupper(substr($i, 1, 1)) substr($i, 2)
      }
    }'
)"

APPLICATION_CLASS="${APPLICATION_PREFIX}Application"
PACKAGE_PATH="$(printf '%s' "$PACKAGE_NAME" | tr '.' '/')"

echo "다음 값으로 프로젝트를 초기화합니다."
echo
echo "Project slug:      $PROJECT_SLUG"
echo "Display name:      $DISPLAY_NAME"
echo "Package name:      $PACKAGE_NAME"
echo "Application class: $APPLICATION_CLASS"
echo

python3 - \
  "$PROJECT_SLUG" \
  "$PACKAGE_NAME" \
  "$DISPLAY_NAME" \
  "$APPLICATION_CLASS" <<'PY'
from pathlib import Path
import sys

project_slug = sys.argv[1]
package_name = sys.argv[2]
display_name = sys.argv[3]
application_class = sys.argv[4]

replacements = {
    "system-design-template": project_slug,
    "System Design Template": display_name,
    "com.backendsystemdesignlab.template": package_name,
    "SystemDesignTemplateApplication": application_class,
}

targets = [
    Path("settings.gradle"),
    Path("docker-compose.yml"),
    Path(".env.example"),
    Path("README.md"),
    Path("src/main/resources/application.yml"),
]

for target in targets:
    if not target.exists():
        continue

    content = target.read_text(encoding="utf-8")

    for old, new in replacements.items():
        content = content.replace(old, new)

    target.write_text(content, encoding="utf-8")

java_targets = [
    *Path("src/main/java").rglob("*.java"),
    *Path("src/test/java").rglob("*.java"),
]

for target in java_targets:
    content = target.read_text(encoding="utf-8")

    for old, new in replacements.items():
        content = content.replace(old, new)

    target.write_text(content, encoding="utf-8")
PY

# Java 패키지 이동 로직 추가
mkdir -p "src/main/java/$PACKAGE_PATH"
mkdir -p "src/test/java/$PACKAGE_PATH"

cp -R \
  "src/main/java/$CURRENT_PACKAGE_PATH/." \
  "src/main/java/$PACKAGE_PATH/"

cp -R \
  "src/test/java/$CURRENT_PACKAGE_PATH/." \
  "src/test/java/$PACKAGE_PATH/"

rm -rf "src/main/java/$CURRENT_PACKAGE_PATH"
rm -rf "src/test/java/$CURRENT_PACKAGE_PATH"

# Application 클래스 파일 이름 변경
MAIN_APPLICATION_FILE="$PACKAGE_PATH/$CURRENT_APPLICATION_CLASS.java"
NEW_MAIN_APPLICATION_FILE="$PACKAGE_PATH/$APPLICATION_CLASS.java"

TEST_APPLICATION_FILE="$PACKAGE_PATH/${CURRENT_APPLICATION_CLASS}Tests.java"
NEW_TEST_APPLICATION_FILE="$PACKAGE_PATH/${APPLICATION_CLASS}Tests.java"

if [[ -f "src/main/java/$MAIN_APPLICATION_FILE" ]]; then
  mv \
    "src/main/java/$MAIN_APPLICATION_FILE" \
    "src/main/java/$NEW_MAIN_APPLICATION_FILE"
fi

if [[ -f "src/test/java/$TEST_APPLICATION_FILE" ]]; then
  mv \
    "src/test/java/$TEST_APPLICATION_FILE" \
    "src/test/java/$NEW_TEST_APPLICATION_FILE"
fi

# 로컬 환경 파일 재생성
rm -f .env
cp .env.example .env

# 초기화 완료 표시
cat <<EOF > .template-initialized
Project slug: $PROJECT_SLUG
Display name: $DISPLAY_NAME
Package name: $PACKAGE_NAME
Application class: $APPLICATION_CLASS
EOF

echo
echo "프로젝트 초기화가 완료됐습니다."
echo
echo "변경 결과:"
echo "- Gradle project: $PROJECT_SLUG"
echo "- Spring application: $PROJECT_SLUG"
echo "- Docker image: $PROJECT_SLUG:local"
echo "- Java package: $PACKAGE_NAME"
echo "- Main class: $APPLICATION_CLASS"
echo
echo "다음 명령으로 검증하세요."
echo
echo "./gradlew clean test"
echo "./scripts/start.sh"
echo "./scripts/run-smoke-test.sh"
