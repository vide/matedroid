#!/usr/bin/env bash
#
# remote-gradle.sh - run Gradle in the build pod on the homelab cluster instead of this machine.
#
# Usage:
#   ./scripts/remote-gradle.sh <gradle tasks and options>    e.g. lintDebug testDebugUnitTest
#   ./scripts/remote-gradle.sh --setup     apply util/k8s-build/matedroid-build.yaml, wait for the pod
#   ./scripts/remote-gradle.sh --shell     interactive shell in the pod's copy of the tree
#   ./scripts/remote-gradle.sh --push      only sync the working tree up
#   ./scripts/remote-gradle.sh --pull      only sync build outputs back
#
# A normal run syncs the working tree into the pod with rsync (git-ignored files, .git and
# build directories excluded, so local.properties and .env never leave the laptop; a stub .git
# carries only the HEAD commit for BuildConfig.GIT_SHA), runs ./gradlew there, and syncs
# app/build/outputs, app/build/reports and app/build/test-results back so APKs can be
# installed and reports read locally. The exit code is Gradle's.
#
# Device tasks (connected*AndroidTest, install*) need adb and stay local. Everything else,
# in particular assemble*, lint*, test* and detekt, belongs here.
#
# Environment:
#   MATEDROID_BUILD_NS   namespace (default matedroid-build); kubectl context as configured

set -euo pipefail

NS="${MATEDROID_BUILD_NS:-matedroid-build}"
REMOTE_ROOT="/data/workspace/matedroid"
MANIFEST="util/k8s-build/matedroid-build.yaml"

# rsync calls this script back as its remote shell: "$0 --rsh <host> <command...>". The host is
# a placeholder; the command runs in the pod.
if [ "${1:-}" = "--rsh" ]; then
    shift 2
    exec kubectl exec -i -n "$NS" "$RGRADLE_POD" -- "$@"
fi

cd "$(dirname "$0")/.."

log() { echo "→ $*" >&2; }
die() { echo "✗ $*" >&2; exit 1; }

command -v kubectl >/dev/null || die "kubectl not found"
command -v rsync >/dev/null || die "rsync not found"

pod() {
    kubectl get pod -n "$NS" -l app=matedroid-build \
        --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}' 2>/dev/null
}

setup() {
    log "Applying $MANIFEST"
    kubectl apply -f "$MANIFEST"
    log "Waiting for the pod (first time pulls a few GB of SDK image)"
    kubectl rollout status deployment/matedroid-build -n "$NS" --timeout=900s
    kubectl exec -n "$NS" "$(pod)" -- bash -c 'until command -v rsync >/dev/null; do sleep 2; done; echo rsync ready' \
        || die "rsync never appeared in the pod"
}

require_pod() {
    RGRADLE_POD="$(pod)"
    [ -n "$RGRADLE_POD" ] || die "No running pod in namespace $NS. Run: $0 --setup"
    export RGRADLE_POD
}

sync_up() {
    log "Syncing tree to $RGRADLE_POD:$REMOTE_ROOT"
    kubectl exec -n "$NS" "$RGRADLE_POD" -- mkdir -p "$REMOTE_ROOT"
    # No --owner/--group: the pod runs as root and git refuses a tree owned by someone else.
    rsync -rlptDz --delete --blocking-io --rsh="$0 --rsh" \
        --exclude=.git --exclude=build/ --exclude=.gradle/ --exclude=.kotlin/ \
        --exclude=local.properties --exclude=.env \
        --filter=':- .gitignore' \
        ./ "pod:$REMOTE_ROOT/"
    # The build embeds `git rev-parse --short HEAD` in BuildConfig. A stub repository whose
    # HEAD points at the local commit satisfies that without shipping the object store.
    local sha
    sha="$(git rev-parse HEAD)"
    kubectl exec -n "$NS" "$RGRADLE_POD" -- bash -c \
        'cd "$1" && chown -R root:root . && rm -rf .git && mkdir -p .git/refs/heads .git/objects && echo "ref: refs/heads/sync" > .git/HEAD && echo "$2" > .git/refs/heads/sync && git config --global safe.directory "$1"' \
        _ "$REMOTE_ROOT" "$sha"
}

sync_down() {
    local rel
    for rel in app/build/outputs app/build/reports app/build/test-results; do
        if kubectl exec -n "$NS" "$RGRADLE_POD" -- test -d "$REMOTE_ROOT/$rel" 2>/dev/null; then
            mkdir -p "$rel"
            rsync -az --blocking-io --rsh="$0 --rsh" "pod:$REMOTE_ROOT/$rel/" "$rel/"
        fi
    done
    log "Outputs synced to app/build/{outputs,reports,test-results}"
}

case "${1:-}" in
    --setup) setup; exit 0 ;;
    --push)  require_pod; sync_up; exit 0 ;;
    --pull)  require_pod; sync_down; exit 0 ;;
    --shell) require_pod; exec kubectl exec -it -n "$NS" "$RGRADLE_POD" -- bash -c "cd $REMOTE_ROOT && exec bash" ;;
    -h|--help|"") sed -n '2,22p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
esac

require_pod
sync_up
log "gradlew $*"
set +e
kubectl exec -n "$NS" "$RGRADLE_POD" -- \
    bash -c 'cd "$1" && shift && exec ./gradlew --console=plain "$@"' _ "$REMOTE_ROOT" "$@"
status=$?
set -e
sync_down
exit "$status"
