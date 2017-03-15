#! /usr/bin/env bash

set -euo pipefail

processTranscriptsFile () {
    ##
    # Getting rid of the first (blank) line of the processed chat transcripts input data
    ##
    echo >&2 "Processing transcripts file"
    removeFirstLine () {
        local filename="$1"
        # remove first line if it's blank
        sed -i '1{/^ *$/d;}' "$filename"
        echo "DONE"
    }

    local defaultfile="transcripts"
    local filename=${1:-$defaultfile}
    local name="$filename".tsv
    local filepath="$PWD"/input/"$name"
    local filepathwithext="$filepath".tar.gz

    if [[ -f "$filepath" ]]; then
        removeFirstLine "$filepath"
    elif [[ -f "$filepathwithext"  ]]; then
        # extract the file
        tar -xzf "$filepathwithext" -C "$PWD/input"
        removeFirstLine "$filepath"
        mv "$filepathwithext" "$filepathwithext".extracted
    else
        echo "NOT DONE"
    fi
}

setupAmmonite () {
    echo >&2 "Setting up Ammonite"
    local url="https://git.io/vMF2M"
    local installpath=/usr/local/bin/amm

    [[ -x "$installpath"  ]] || (
        sudo curl -L -o "$installpath"  $url \
            && sudo chmod +x "$installpath"
    )
}

preCompileScripts () {
    echo >&2 "Pre-Compiling scala scripts, if any"
    local defaultpath=/usr/local/bin/amm
    local ammpath=${1:-$defaultpath}
    local scriptfilespat="$PWD/udf/*.sc"

    local filenamewithext
    local filename
    for f in $scriptfilespat; do
        filenamewithext="${f##*/}"
        filename="${filenamewithext%.*}"
        local code="import \$file.udf.$filename"
        echo >&2 "Compiling $filenamewithext"
        echo  "$code" \
            | $ammpath >/dev/null 2>&1
    done
}


setupBazaar () {
    echo >&2 "Setting up Bazaar"
    local url="https://github.com/kaychaks/bazaar.git"
    : "${BAZAAR_HOME:=$PWD/udf/bazaar}"
    [[ -x "$BAZAAR_HOME"/parser/target/start ]] \
        || (
        echo >&2 "Setting up Bazaar/Parser"
        mkdir -p "$BAZAAR_HOME"
        cd "$BAZAAR_HOME"
        [[ -d .git ]] \
            || git clone $url .
        cd parser
        : "${SBT_OPTS:=-Xmx1g}"
        export SBT_OPTS
        ./setup.sh
    )
}

main () {
    processTranscriptsFile
    setupAmmonite \
        && preCompileScripts
    setupBazaar
}

main
