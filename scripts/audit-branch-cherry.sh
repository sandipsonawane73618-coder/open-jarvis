#!/usr/bin/env bash
set -euo pipefail

BASE_REF="${1:-origin/feat/jarvis-all-in-one}"
OUT_DIR="artifacts"
OUT_FILE="$OUT_DIR/branch-cherry-counts.txt"
mkdir -p "$OUT_DIR"

git rev-parse --verify "$BASE_REF^{commit}" >/dev/null
{
  echo "git cherry audit"
  echo "base=$BASE_REF"
  echo "base_sha=$(git rev-parse "$BASE_REF")"
  echo "branch\tnot_in_all_in_one\tpatch_equivalent\ttotal"
} > "$OUT_FILE"

mapfile -t refs < <(git for-each-ref --format='%(refname:short)' refs/remotes/origin | grep -E '^origin/(feat|feature)/' | sort)
if [[ "${#refs[@]}" -eq 0 ]]; then
  echo "No remote feat/* or feature/* branches found" >&2
  exit 1
fi

for ref in "${refs[@]}"; do
  detail="$(git cherry -v "$BASE_REF" "$ref")"
  plus="$(printf '%s\\n' "$detail" | awk '/^\\+/ {n++} END {print n+0}')"
  minus="$(printf '%s\\n' "$detail" | awk '/^-/ {n++} END {print n+0}')"
  total=$((plus + minus))
  printf '%s\\t%s\\t%s\\t%s\\n' "$ref" "$plus" "$minus" "$total" | tee -a "$OUT_FILE"
  if [[ -n "$detail" ]]; then
    {
      echo
      echo "[$ref]"
      printf '%s\\n' "$detail"
    } >> "$OUT_FILE"
  fi
done

echo "Audited ${#refs[@]} remote feat/* and feature/* branches against $BASE_REF."
