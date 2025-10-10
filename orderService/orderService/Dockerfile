# Utiliser une image de base avec JDK 21
FROM openjdk:21-jdk-slim

# Définir le répertoire de travail
WORKDIR /app

# Copier le fichier pom.xml et les scripts mvnw
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn

# Rendre le script mvnw exécutable, télécharger les dépendances et installer curl
RUN chmod +x ./mvnw && \
    ./mvnw dependency:go-offline -B && \
    apt-get update && \
    apt-get install -y curl && \
    rm -rf /var/lib/apt/lists/*

# Copier le code source
COPY src ./src

# Construire l'application
RUN ./mvnw clean package -DskipTests

# Exposer le port
EXPOSE 8084

# Définir les variables d'environnement par défaut
ENV SPRING_PROFILES_ACTIVE=docker
ENV JWT_SECRET=YnJva2VyWFNlY3JldEtleUZvckpXVFNpZ25pbmdTdXBlckxvbmdTdHJpbmdUaGF0SXNTZWN1cmU=

# Commande pour démarrer l'application
CMD ["java", "-jar", "target/orderService-0.0.1-SNAPSHOT.jar"]