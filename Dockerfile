# ─────────────────────────────────────────────────────────
#  Dockerfile Render — CORBA PDF Server 1.8
#  Compile serveur + bridge, lance orbd + serveur + Tomcat
# ─────────────────────────────────────────────────────────

# ── Étape 1 : Build Maven ──────────────────────────────
FROM maven:3.8.6-openjdk-8 AS builder

WORKDIR /build

# Compiler l'IDL
COPY idl/PDFService.idl /build/idl/PDFService.idl
RUN mkdir -p /build/server/src/main/java \
             /build/bridge/src/main/java && \
    idlj -fall -td /build/server/src/main/java /build/idl/PDFService.idl && \
    idlj -fall -td /build/bridge/src/main/java /build/idl/PDFService.idl

# Build serveur CORBA
COPY server/pom.xml /build/server/pom.xml
COPY server/src /build/server/src
RUN cd /build/server && mvn clean package -DskipTests -q \
    -Dmaven.wagon.http.ssl.insecure=true \
    -Dmaven.wagon.http.ssl.allowall=true

# Build bridge Tomcat
COPY bridge/pom.xml /build/bridge/pom.xml
COPY bridge/src /build/bridge/src
RUN cd /build/bridge && mvn clean package -DskipTests -q \
    -Dmaven.wagon.http.ssl.insecure=true \
    -Dmaven.wagon.http.ssl.allowall=true
# ── Étape 2 : Image runtime ────────────────────────────
FROM tomcat:8.5-jdk8-openjdk-slim

WORKDIR /app

# Installer les outils nécessaires
RUN apt-get update && apt-get install -y --no-install-recommends \
    procps curl \
    fontconfig \
    fonts-dejavu-core \
    libfontconfig1 && \
    fc-cache -fv && \
    rm -rf /var/lib/apt/lists/*

# Copier le JAR du serveur CORBA
COPY --from=builder \
    /build/server/target/corba-pdf-server-1.8-jar-with-dependencies.jar \
    /app/corba-pdf-server.jar

# Déployer le bridge dans Tomcat
RUN rm -rf /usr/local/tomcat/webapps/*
COPY --from=builder \
    /build/bridge/target/bridge.war \
    /usr/local/tomcat/webapps/ROOT.war

# Répertoire pour les images générées
RUN mkdir -p /tmp/corba_pdf_output /tmp/orbd_db

# Script de démarrage — lance orbd + serveur CORBA + Tomcat
COPY start.sh /app/start.sh
RUN sed -i 's/\r$//' /app/start.sh && chmod +x /app/start.sh

# Port exposé (Render utilise la variable PORT)
EXPOSE 8080

CMD ["sh", "/app/start.sh"]
