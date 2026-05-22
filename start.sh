#!/bin/bash

echo "========================================="
echo "  CORBA PDF Server 1.8 — Render Startup"
echo "========================================="

PORT=${PORT:-8080}
CORBA_HOST="127.0.0.1"
CORBA_PORT="900"
#!/bin/bash

echo "========================================="
echo "  CORBA PDF Server 1.8 — Render Startup"
echo "========================================="

PORT=${PORT:-8080}
CORBA_HOST="127.0.0.1"
CORBA_PORT="900"

# 1. orbd
echo "[1/3] Démarrage orbd..."
orbd -ORBInitialPort $CORBA_PORT -ORBInitialHost 0.0.0.0 -defaultdb /tmp/orbd_db &
ORBD_PID=$!
sleep 8
echo "      orbd OK (PID: $ORBD_PID)"

# 2. Serveur CORBA
echo "[2/3] Démarrage serveur CORBA..."
java -Dorg.omg.CORBA.ORBInitialHost=$CORBA_HOST \
     -Dorg.omg.CORBA.ORBInitialPort=$CORBA_PORT \
     -Djava.awt.headless=true \
     -classpath /app/corba-pdf-server.jar \
     com.corba.pdf.PDFServer \
     -ORBInitialHost $CORBA_HOST \
     -ORBInitialPort $CORBA_PORT &
SERVER_PID=$!
sleep 10
echo "      Serveur CORBA OK (PID: $SERVER_PID)"

# 3. Tomcat
echo "[3/3] Démarrage Tomcat..."
export CORBA_HOST=$CORBA_HOST
export CORBA_PORT=$CORBA_PORT
if [ "$PORT" != "8080" ]; then
    sed -i "s/port=\"8080\"/port=\"$PORT\"/" /usr/local/tomcat/conf/server.xml
fi
catalina.sh start
echo "      Tomcat OK sur port $PORT"

# Keep-alive toutes les 14 min
while true; do
    sleep 840
    curl -s https://corba-render.onrender.com/api/health > /dev/null 2>&1
    echo "[KEEP-ALIVE] $(date)"
done &

# Garder le conteneur vivant
tail -f /usr/local/tomcat/logs/catalina.out
EOF
