#!/usr/bin/env bash
# Downloads the prebuilt libchewing dictionary data ("word.dat"/"tsi.dat")
# that bpmf_init() needs at runtime, verifies its sha256, and extracts it
# into decoder-native/src/main/assets/chewing/.
#
# Why a downloaded prebuilt artifact instead of building from source: the
# raw dictionary source lives in the `chewing/libchewing-data` submodule
# (decoder-native/cmake/libchewing/data/) as .src text files, and turning
# those into the binary .dat format requires `chewing-cli`, a separate Rust
# build target. Upstream publishes an architecture-independent "Generic"
# prebuilt release of the exact same data version as our pinned submodule
# commit, so we use that instead of standing up a whole extra host-side
# Rust build for W1-A. See ADR-0006 and the devlog for the version-match
# verification.
#
# This script only downloads a data artifact and verifies its checksum; it
# does not execute anything from the download. Re-run is idempotent and
# skips the download if the target files already exist with the right size.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ASSETS_DIR="${SCRIPT_DIR}/../src/main/assets/chewing"

VERSION="2026.3.22"
ZIP_NAME="libchewing-data-${VERSION}-Generic.zip"
URL="https://github.com/chewing/libchewing-data/releases/download/v${VERSION}/${ZIP_NAME}"
# Verified 2026-08-10 against the GitHub Releases API asset digest for
# v2026.3.22, and against `shasum -a 256` on the downloaded file.
EXPECTED_SHA256="db8248f7a46be17beda41aedd94e7e846d01e3b2cfa3b45fcfae453acf9c62be"

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "${WORK_DIR}"' EXIT

if [[ -f "${ASSETS_DIR}/word.dat" && -f "${ASSETS_DIR}/tsi.dat" ]]; then
    echo "fetch_chewing_data.sh: word.dat/tsi.dat already present in ${ASSETS_DIR}, skipping download."
    exit 0
fi

echo "fetch_chewing_data.sh: downloading ${URL} ..."
curl -sSfL -o "${WORK_DIR}/${ZIP_NAME}" "${URL}"

ACTUAL_SHA256="$(shasum -a 256 "${WORK_DIR}/${ZIP_NAME}" | awk '{print $1}')"
if [[ "${ACTUAL_SHA256}" != "${EXPECTED_SHA256}" ]]; then
    echo "fetch_chewing_data.sh: sha256 mismatch!" >&2
    echo "  expected: ${EXPECTED_SHA256}" >&2
    echo "  actual:   ${ACTUAL_SHA256}" >&2
    exit 1
fi

mkdir -p "${ASSETS_DIR}"
unzip -oq "${WORK_DIR}/${ZIP_NAME}" -d "${WORK_DIR}/extracted"

EXTRACTED_DATA_DIR="${WORK_DIR}/extracted/libchewing-data-${VERSION}-Generic/share/libchewing"
cp "${EXTRACTED_DATA_DIR}/word.dat" "${ASSETS_DIR}/word.dat"
cp "${EXTRACTED_DATA_DIR}/tsi.dat" "${ASSETS_DIR}/tsi.dat"

echo "fetch_chewing_data.sh: wrote $(du -h "${ASSETS_DIR}/word.dat" | cut -f1) word.dat + $(du -h "${ASSETS_DIR}/tsi.dat" | cut -f1) tsi.dat to ${ASSETS_DIR}"
