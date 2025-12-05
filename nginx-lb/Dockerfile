FROM nginx:alpine

# Copier la configuration NGINX
COPY nginx.conf /etc/nginx/nginx.conf

# Exposer le port
EXPOSE 8080

# Démarrer NGINX
CMD ["nginx", "-g", "daemon off;"]
