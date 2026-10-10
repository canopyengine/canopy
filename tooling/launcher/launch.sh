#!/bin/sh
# Build with Gradle, then give the installed application the real terminal.
set -eu

launcher_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project=.
module=:
usage() {
    echo 'Usage: launch.sh [-p PROJECT] [--module :game] [-- GAME_ARGS...]'
}
while [ "$#" -gt 0 ]; do
    case "$1" in
        -p|--project) [ "$#" -ge 2 ] || { usage >&2; exit 2; }; project=$2; shift 2 ;;
        --module) [ "$#" -ge 2 ] || { usage >&2; exit 2; }; module=$2; shift 2 ;;
        --) shift; break ;;
        -h|--help) usage; exit 0 ;;
        *) usage >&2; exit 2 ;;
    esac
done
case "$module" in
    :) task=:canopyLaunchManifest ;;
    :*)
        case "$module" in *[!A-Za-z0-9_:-]*|*::*|*:) echo 'Invalid module path' >&2; exit 2 ;; esac
        task=$module:canopyLaunchManifest ;;
    *) echo 'Module must start with :' >&2; exit 2 ;;
esac
cd -- "$project"
[ -f gradlew ] || { echo 'No Gradle wrapper in project directory' >&2; exit 2; }
manifest=$(mktemp "${TMPDIR:-/tmp}/canopy-launch.XXXXXXXX")
trap 'rm -f -- "$manifest"' 0
trap 'exit 130' INT
trap 'exit 143' TERM
sh ./gradlew --console=plain -I "$launcher_dir/launcher.gradle.kts" \
    "-PcanopyLaunchManifest=$manifest" "$task" </dev/null
{
    IFS= read -r app_dir
    IFS= read -r java_home
    IFS= read -r app_script
} < "$manifest"
rm -f -- "$manifest"
trap - 0 INT TERM
cd -- "$app_dir"
JAVA_HOME=$java_home
export JAVA_HOME
exec sh "$app_script" "$@"
