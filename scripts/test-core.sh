#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if ! command -v javac >/dev/null && [[ -x /workspace/.tools/jdk-21.0.8+9/bin/javac ]]; then
    export PATH="/workspace/.tools/jdk-21.0.8+9/bin:$PATH"
fi
output_dir=$(mktemp -d)
trap 'rm -rf "$output_dir"' EXIT
javac --release 21 -d "$output_dir" src/main/java/dev/levrn/core/*.java src/coreTest/java/dev/levrn/core/CoreTest.java
java -cp "$output_dir" dev.levrn.core.CoreTest
