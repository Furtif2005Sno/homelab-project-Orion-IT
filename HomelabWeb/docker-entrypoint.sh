#!/bin/sh
# Starts as root only to make the data directory writable (a bind-mounted ./data is created
# root-owned by Docker), then drops to the unprivileged "node" user for the server itself.
set -e

DATA_DIR="${DATA_DIR:-/data}"

if [ "$(id -u)" = "0" ]; then
  mkdir -p "$DATA_DIR"
  chown -R node:node "$DATA_DIR" 2>/dev/null || echo "homelab-web: could not chown $DATA_DIR, the server may not be able to write it" >&2
  exec setpriv --reuid=node --regid=node --init-groups "$@"
fi

exec "$@"
