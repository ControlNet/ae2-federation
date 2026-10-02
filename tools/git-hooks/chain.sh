# Sourced by the hooks here: runs the hook of the same name from the global hooks directory, if there is one, so
# that pointing this repository's core.hooksPath here keeps the user's global hooks.
chain_global() {
    name=$1
    shift
    dir=$(git config --global --get core.hooksPath) || return 0
    case "$dir" in "~/"*) dir="$HOME/${dir#\~/}" ;; esac
    [ -x "$dir/$name" ] || return 0
    "$dir/$name" "$@"
}
