#!/usr/bin/env bash
set -euo pipefail
rm -rf third_party/llama.cpp
mkdir -p third_party
git clone --depth 1 https://github.com/ggml-org/llama.cpp.git third_party/llama.cpp
