#!/usr/bin/env bash
utilpath=${HOME}/local/util
[[ ":$PATH:" != *"${utilpath}"* ]] &&  PATH="${utilpath}:${PATH}"
