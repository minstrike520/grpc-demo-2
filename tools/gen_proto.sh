#!/usr/bin/env bash
# Regenerate gRPC/protobuf stubs for both the python server and the android app.
#
# Requirements: uv, curl, unzip, java (for the grpc-kotlin plugin jar)
#
# Output (committed to the repo so builds need no codegen):
#   server/sim_pb2.py, server/sim_pb2_grpc.py        python stubs
#   android/app/src/main/java/sim/                   java + grpc-kotlin stubs
set -euo pipefail
cd "$(dirname "$0")/.."

OUT_PY=server
OUT_ANDROID=android/app/src/main/java
PROTOC_VER=25.3
CACHE_DIR=${XDG_CACHE_HOME:-$HOME/.cache}/grpc-demo/protoc
PROTOC_ZIP="$CACHE_DIR/protoc-$PROTOC_VER-linux-x86_64.zip"
PROTOC_BIN="$CACHE_DIR/protoc-$PROTOC_VER/bin/protoc"

echo "==> protoc (official release, has the java/kotlin builtin generators)"
mkdir -p "$CACHE_DIR"
if [ ! -f "$PROTOC_ZIP" ]; then
  curl -fsSL -o "$PROTOC_ZIP" \
    "https://github.com/protocolbuffers/protobuf/releases/download/v$PROTOC_VER/protoc-$PROTOC_VER-linux-x86_64.zip"
fi
if [ ! -x "$PROTOC_BIN" ]; then
  unzip -oq "$PROTOC_ZIP" -d "$CACHE_DIR/protoc-$PROTOC_VER"
  chmod +x "$PROTOC_BIN"
fi
PROTOC_BIN=$(realpath "$PROTOC_BIN")

echo "==> grpc-kotlin plugin (generates the coroutine stubs)"
PLUGIN_JAR=$(mktemp --suffix=.jar)
PLUGIN_WRAP=$(mktemp)
GRPC_JAVA_EXE=$(mktemp)
trap 'rm -f "$PLUGIN_JAR" "$PLUGIN_WRAP" "$GRPC_JAVA_EXE"' EXIT
curl -fsSL -o "$PLUGIN_JAR" \
  https://repo1.maven.org/maven2/io/grpc/protoc-gen-grpc-kotlin/1.4.3/protoc-gen-grpc-kotlin-1.4.3-jdk8.jar
cat > "$PLUGIN_WRAP" <<EOF
#!/usr/bin/env bash
exec java -jar "$PLUGIN_JAR" "\$@"
EOF
chmod +x "$PLUGIN_WRAP"
# the kotlin stubs delegate to the generated java base stub (SimServiceGrpc)
curl -fsSL -o "$GRPC_JAVA_EXE" \
  https://repo1.maven.org/maven2/io/grpc/protoc-gen-grpc-java/1.68.1/protoc-gen-grpc-java-1.68.1-linux-x86_64.exe
chmod +x "$GRPC_JAVA_EXE"

echo "==> python stubs"
uv run --project server python -m grpc_tools.protoc \
  -I proto \
  --python_out="$OUT_PY" \
  --grpc_python_out="$OUT_PY" \
  proto/sim.proto

echo "==> android stubs (java messages + kotlin stubs)"
"$PROTOC_BIN" \
  -I proto \
  --plugin=protoc-gen-grpc-kotlin="$PLUGIN_WRAP" \
  --plugin=protoc-gen-grpc-java="$GRPC_JAVA_EXE" \
  --java_out=lite:"$OUT_ANDROID" \
  --grpc-java_out=lite:"$OUT_ANDROID" \
  --grpc-kotlin_out="$OUT_ANDROID" \
  proto/sim.proto

echo "==> done"
ls -1 "$OUT_PY"/*sim* "$OUT_ANDROID"/sim