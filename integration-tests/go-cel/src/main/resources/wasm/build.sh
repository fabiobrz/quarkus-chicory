#! /bin/bash
set -euxo pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

(
    cd ${SCRIPT_DIR}
    echo "Building Go CEL WASM module..."
    echo ""
    echo "Step 1: Building Docker image..."
    docker build -t go-cel-builder .
    echo ""
    echo "Step 2: Extracting WASM file..."
    docker run --rm go-cel-builder > go-cel.wasm
    echo ""
    echo "  Build complete!"
    echo "  Output: go-cel.wasm ($(du -h go-cel.wasm | cut -f1))"
    echo ""
)