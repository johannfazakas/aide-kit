#!/bin/bash
set -u

input=$(cat)
file_path=$(printf '%s' "$input" | jq -r '.tool_input.file_path // empty' 2>/dev/null)

[[ -z "$file_path" ]] && exit 0
case "$file_path" in
  *.kt | *.kts) ;;
  *) exit 0 ;;
esac
[[ -f "$file_path" ]] || exit 0

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
version=$(sed -n 's/^ktlint = "\(.*\)"$/\1/p' "$repo_root/gradle/libs.versions.toml")
if [[ -z "$version" ]]; then
  echo "ktlint hook: could not resolve ktlint version from gradle/libs.versions.toml; skipping check" >&2
  exit 0
fi

cache_dir="${XDG_CACHE_HOME:-$HOME/.cache}/ktlint/$version"
bin="$cache_dir/ktlint"
if [[ ! -x "$bin" ]]; then
  mkdir -p "$cache_dir"
  if ! curl -fsSL "https://github.com/ktlint/ktlint/releases/download/$version/ktlint" -o "$bin"; then
    echo "ktlint hook: failed to download ktlint $version; skipping check (CI will still enforce lint)" >&2
    rm -f "$bin"
    exit 0
  fi
  chmod +x "$bin"
fi

if ! report=$("$bin" "$file_path" 2>&1); then
  {
    echo "ktlint violations in $file_path (run ./gradlew ktlintFormat to auto-fix):"
    echo "$report"
  } >&2
  exit 2
fi
exit 0
