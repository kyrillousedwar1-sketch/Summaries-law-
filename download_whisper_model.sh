#!/usr/bin/env bash
set -euo pipefail
mkdir -p app/src/main/assets/model
curl -L --fail -o /tmp/whisper.tar.bz2  https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-small.tar.bz2
rm -rf /tmp/sherpa-whisper
mkdir -p /tmp/sherpa-whisper
tar -xjf /tmp/whisper.tar.bz2 -C /tmp/sherpa-whisper
DIR=$(find /tmp/sherpa-whisper -type d -name 'sherpa-onnx-whisper-small*' | head -1)
test -n "$DIR"
cp "$DIR"/small-encoder.int8.onnx app/src/main/assets/model/whisper-encoder.onnx
cp "$DIR"/small-decoder.int8.onnx app/src/main/assets/model/whisper-decoder.onnx
cp "$DIR"/small-tokens.txt app/src/main/assets/model/tokens.txt
