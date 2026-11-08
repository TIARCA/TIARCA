#!/usr/bin/env bash
# Install the pinned official Gradle distribution via GitHub's release asset API.
# This avoids the public browser download URL returning intermittent HTTP 504.
# The ZIP is always checked against the pinned SHA-256, including cache hits.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
properties="${repo_root}/gradle/wrapper/gradle-wrapper.properties"
version="$(sed -nE 's@^distributionUrl=.*gradle-([0-9.]+)-bin\\.zip$@\\1@p' "${properties}")"
expected_sha="$(sed -nE 's/^distributionSha256Sum=([a-f0-9]{64})$/\\1/p' "${properties}")"

if [[ -z "${version}" || -z "${expected_sha}" ]]; then
    echo "Missing pinned Gradle version or SHA-256 in wrapper properties" >&2
    exit 1
fi

archive_dir="${RUNNER_TEMP}/tiarca-gradle-archive"
archive="${archive_dir}/gradle-${version}-bin.zip"
install_dir="${RUNNER_TEMP}/tiarca-gradle-install"
gradle_bin="${install_dir}/gradle-${version}/bin"
mkdir -p "${archive_dir}"

verify_archive() {
    printf '%s  %s\\n' "${expected_sha}" "${archive}" | sha256sum -c --status
}

if [[ -f "${archive}" ]] && verify_archive; then
    echo "Reusing cached Gradle ${version} archive (SHA-256 verified)."
else
    rm -f "${archive}"
    downloaded=false
    for attempt in 1 2 3; do
        echo "Downloading official Gradle ${version} release asset via GitHub API (attempt ${attempt}/3)."
        rm -f "${archive}"
        if gh release download "v${version}" \\
            --repo gradle/gradle-distributions \\
            --pattern "gradle-${version}-bin.zip" \\
            --dir "${archive_dir}" && verify_archive; then
            downloaded=true
            break
        fi
        if [[ "${attempt}" -lt 3 ]]; then
            sleep "$((attempt * 10))"
        fi
    done
    if [[ "${downloaded}" != true ]]; then
        echo "Cannot obtain a checksum-verified Gradle ${version} ZIP from the official release." >&2
        exit 1
    fi
fi

mkdir -p "${install_dir}"
unzip -q "${archive}" -d "${install_dir}"
test -x "${gradle_bin}/gradle"
echo "${gradle_bin}" >> "${GITHUB_PATH}"
echo "Pinned Gradle ${version} installed and SHA-256 verified."
