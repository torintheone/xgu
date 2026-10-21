#!/usr/bin/env bash
# Creates the permanent release signing key and a text file with the four values for GitHub Secrets.
# Needs Java (keytool). Run it once: from the repository root, `bash scripts/make-keystore.sh`.
set -euo pipefail

cd "$(dirname "$0")/.."
OUT_DIR="keystore"
KEYSTORE="$OUT_DIR/petri-release.jks"
SECRETS="$OUT_DIR/github-secrets.txt"
ALIAS="petri"

if ! command -v keytool > /dev/null; then
  echo "Не найдена программа keytool: нужна установленная Java (JDK)." >&2
  exit 1
fi
if [ -e "$KEYSTORE" ]; then
  echo "Ключ уже есть: $KEYSTORE. Второй не создаю, чтобы не потерять первый." >&2
  exit 1
fi

mkdir -p "$OUT_DIR"
PASSWORD="$(od -An -N16 -tx1 /dev/urandom | tr -d ' \n')"

keytool -genkeypair -noprompt \
  -keystore "$KEYSTORE" -storetype PKCS12 -storepass "$PASSWORD" \
  -alias "$ALIAS" -keypass "$PASSWORD" \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Petri" > /dev/null 2>&1

cat > "$SECRETS" <<EOF
Четыре секрета для GitHub: Settings → Secrets and variables → Actions → New repository secret.
Name — строка с заглавными буквами, Secret — строка под ней (целиком, без пробелов по краям).

KEYSTORE_BASE64
$(base64 < "$KEYSTORE" | tr -d '\n')

KEYSTORE_PASSWORD
$PASSWORD

KEY_ALIAS
$ALIAS

KEY_PASSWORD
$PASSWORD
EOF

echo "Готово."
echo "  Ключ:              $KEYSTORE"
echo "  Значения секретов: $SECRETS"
echo "Скачайте оба файла и храните в надёжном месте. Не добавляйте их в репозиторий и никому не пересылайте."
