#!/bin/sh
set -e

# Replace placeholder in index.html with env var (e.g., API_BASE)
if [ -n "$API_BASE" ]; then
  sed -i "s|__API_BASE__|$API_BASE|g" /usr/share/nginx/html/index.html
fi

exec nginx -g 'daemon off;'
