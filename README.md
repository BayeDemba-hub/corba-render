# CORBA PDF Server 1.8 — Déploiement Render

## 🚀 Déployer sur Render

### Étape 1 — Pousser sur GitHub

```bash
git init
git add .
git commit -m "CORBA PDF Server 1.8"
git branch -M main
git remote add origin https://github.com/TON-USERNAME/corba-pdf-server.git
git push -u origin main
```

### Étape 2 — Créer le service sur Render

1. Va sur **https://render.com**
2. Clique **New → Web Service**
3. Connecte ton repo GitHub
4. Render détecte automatiquement le `Dockerfile`
5. Paramètres :
   - **Name** : `corba-pdf-server`
   - **Region** : Frankfurt (EU) — plus proche du Sénégal
   - **Plan** : Free
6. Clique **Create Web Service**

### Étape 3 — Configurer Netlify

Une fois Render déployé, tu auras une URL comme :
```
https://corba-pdf-server.onrender.com
```

Sur ton interface Netlify :
1. Ouvre `https://aquamarine-macaron-ae69ac.netlify.app`
2. Dans la barre de configuration en haut, entre :
   ```
   https://corba-pdf-server.onrender.com/api
   ```
3. Clique **Connecter**
4. Le voyant devient vert ✓

## 📁 Structure

```
corba-render/
├── Dockerfile        ← Image unique (orbd + CORBA + Tomcat)
├── render.yaml       ← Configuration Render
├── start.sh          ← Script de démarrage
├── idl/              ← Interface CORBA (.idl)
├── server/           ← Serveur CORBA + PDFBox
└── bridge/           ← Bridge REST (Tomcat)
```

## ⚠ Note sur le plan gratuit Render

Le plan gratuit Render **s'endort après 15 minutes** d'inactivité.
La première requête après le réveil prend ~30 secondes.
Pour éviter ça, utilise le plan **Starter ($7/mois)**.
