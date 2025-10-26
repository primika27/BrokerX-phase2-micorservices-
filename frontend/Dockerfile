FROM nginx:1.27-alpine

# Copy optional global config first (so it layers well)
COPY nginx/nginx.conf /etc/nginx/nginx.conf

# Copy site content
COPY site/ /usr/share/nginx/html/

# Copy server block
COPY nginx/default.conf /etc/nginx/conf.d/default.conf

# (Optional) ENV-based templating, if you want to inject API URL into index.html at runtime:
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh
ENTRYPOINT ["/entrypoint.sh"]

# Default CMD runs nginx
