#! /usr/bin/env bash

set -euo pipefail
readonly PROGDIR=$(readlink -m "$(dirname "$0")")
readonly BAZAARPATH=$PROGDIR/bazaar/parser
: "${DEEPDIVE_HOME:=$HOME/local}"

isBazaarSetup (){
    local bazaartargetpath=$BAZAARPATH/target/start
    [[ -x "$bazaartargetpath"  ]] \
        || {
        echo "No Bazaar/Parser set up at: $BAZAARPATH"
        exit 2
    } >&2
}

tsvToJSON (){
    set -- id chat
    tsv2json "$@"
}

parse (){
    local parserpath=$BAZAARPATH/run.sh
    local parserargs="-i json -k id -v chat -l 200"

    "$parserpath" "$parserargs"
}

fixCharacters (){
    sed -e "$(for ch in b f r v; do printf 's/\'$ch'/\\\\'$ch'/g;'; done)" -e 's/[.[\*^$()+?|@]/\\\\&/g' -e 's/\]/\\\\&/g' 
}

main (){
    isBazaarSetup \
        && tsvToJSON "$*"\
            | parse \
            | fixCharacters
}

main "$@"
