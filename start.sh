#!/bin/bash
set -e

echo "========================================="
echo "  CORBA PDF Server 1.8 — Render Startup"
echo "========================================="

# Port Render (variable d'environnement)
PORT=${PORT:-8080}
CORBA_HOST="127.0.0.1"
CORBA_PORT="900"

# ── 1. Lancer orbd (NameService CORBA) ──
echo "[1/3] Démarrage orbd (NameService)..."
orbd -ORBInitialPort $CORBA_PORT \
     -ORBInitialHost 0.0.0.0 \
     -defaultdb /tmp/orbd_db &
ORBD_PID=$!
echo "      orbd PID: $ORBD_PID"

# Attendre que orbd soit prêt
sleep 5
echo "      orbd démarré ✓"

# ── 2. Lancer le serveur CORBA + PDFBox ──
echo "[2/3] Démarrage serveur CORBA PDFBox..."
java \
    -Dorg.omg.CORBA.ORBInitialHost=$CORBA_HOST \
    -Dorg.omg.CORBA.ORBInitialPort=$CORBA_PORT \
    -classpath /app/corba-pdf-server.jar \
    com.corba.pdf.PDFServer \
    -ORBInitialHost $CORBA_HOST \
    -ORBInitialPort $CORBA_PORT &
SERVER_PID=$!
echo "      Serveur CORBA PID: $SERVER_PID"

# Attendre que le serveur s'enregistre dans le NameService
sleep 8
echo "      Serveur CORBA démarré ✓"

# ── 3. Configurer et lancer Tomcat (bridge REST) ──
echo "[3/3] Démarrage Tomcat bridge REST..."

# Passer les variables au bridge via les variables d'environnement Tomcat
export CORBA_HOST=$CORBA_HOST
export CORBA_PORT=$CORBA_PORT

# Render impose le port via $PORT — reconfigurer Tomcat
if [ "$PORT" != "8080" ]; then
    sed -i "s/port=\"8080\"/port=\"$PORT\"/" \
        /usr/local/tomcat/conf/server.xml
fi

# Lancer Tomcat en arrière-plan
catalina.sh start

echo ""
echo "========================================="
echo "  Tous les services démarrés !"
echo "  Bridge REST : http://0.0.0.0:$PORT"
echo "  CORBA orbd  : $CORBA_HOST:$CORBA_PORT"
echo "========================================="
# Ping automatique toutes les 14 minutes pour garder Render éveillé
while true; do
    sleep 60
    curl -s https://corba-render.onrender.com/api/health > /dev/null 2>&1
    echo "[KEEP-ALIVE] ping $(date)"
done &
# Garder le conteneur vivant et surveiller les processus
wait_for_death() {
    while true; do
        if ! kill -0 $ORBD_PID 2>/dev/null; then
            echo "[ERREUR] orbd s'est arrêté !"
            break
        fi
        if ! kill -0 $SERVER_PID 2>/dev/null; then
            echo "[ERREUR] Serveur CORBA s'est arrêté !"
            break
        fi
        sleep 10
    done
}

# Attendre les logs Tomcat et surveiller
tail -f /usr/local/tomcat/logs/catalina.out &
wait_for_death
