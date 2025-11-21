# Utiliser Eclipse Temurin JDK 17 comme image de base
FROM eclipse-temurin:17-jdk-alpine

# Définir le répertoire de travail
WORKDIR /app

# Copier le fichier pom.xml et les scripts mvnw
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn

# Rendre le script mvnw exécutable, télécharger les dépendances et installer curl
RUN apk add --no-cache curl dos2unix && \
    dos2unix ./mvnw && \
    chmod +x ./mvnw && \
    ./mvnw dependency:go-offline -B

# Copier le code source
COPY src ./src

# Construire l'application
RUN ./mvnw clean package -DskipTests

# Créer le répertoire pour la base de données avec les bonnes permissions
RUN mkdir -p /data && chmod 777 /data

# Exposer le port 8086
EXPOSE 8086

# Variables d'environnement
ENV SPRING_PROFILES_ACTIVE=docker
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Commande pour démarrer l'application
CMD ["java", "-jar", "target/market-data-service-0.0.1-SNAPSHOT.jar"]