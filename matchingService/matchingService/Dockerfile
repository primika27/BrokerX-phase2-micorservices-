# Utiliser une approche simple avec compilation locale
FROM openjdk:21-jdk-slim

# Définir le répertoire de travail
WORKDIR /app

# Installer Maven
RUN apt-get update && \
    apt-get install -y maven curl && \
    rm -rf /var/lib/apt/lists/*

# Copier tout le projet
COPY . .

# Construire l'application avec Maven
RUN mvn clean package -DskipTests

# Exposer le port
EXPOSE 8085

# Définir les variables d'environnement par défaut
ENV SPRING_PROFILES_ACTIVE=docker
ENV JWT_SECRET=YnJva2VyWFNlY3JldEtleUZvckpXVFNpZ25pbmdTdXBlckxvbmdTdHJpbmdUaGF0SXNTZWN1cmU=

# Commande pour démarrer l'application
CMD ["java", "-jar", "target/matchingService-0.0.1-SNAPSHOT.jar"]