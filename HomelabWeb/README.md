# Homelab Web

Tableau de bord web de Homelab, pensé pour le PC : importe la sauvegarde de l'app mobile et suis tous tes services depuis le navigateur.

- **Import / export** du fichier `.homelab` de l'app (même chiffrement AES-256-GCM, compatible dans les deux sens). Le déchiffrement de l'import se fait dans le navigateur, le mot de passe n'est jamais envoyé au serveur.
- **Suivi en direct** : statut, latence et métrique clé par service (Radarr, Sonarr, qBittorrent, Gluetun, Proxmox, PeaNUT, SafeLine, AMP, Uptime Kuma…), rafraîchi chaque minute.
- **Connexion OIDC** (n'importe quel fournisseur avec découverte standard), avec liste d'utilisateurs autorisés optionnelle.
- Les identifiants des services sont stockés chiffrés dans SQLite (`/data/homelab.db`).

## Lancer avec Docker

```bash
cp .env.example .env        # puis remplis les valeurs
mkdir -p data && sudo chown 1000:1000 data
docker compose -f docker-compose.example.yml up -d --build
```

Le conteneur tourne en utilisateur `node` (uid 1000) : le dossier monté sur `/data` doit lui appartenir.

Côté fournisseur OIDC, crée un client confidentiel avec l'URI de redirection `${APP_URL}/api/auth/callback`.

### Variables d'environnement

| Variable | Requis | Rôle |
| --- | --- | --- |
| `APP_URL` | oui | URL publique du dashboard (sert à construire l'URI de redirection OIDC) |
| `OIDC_ISSUER` | oui | Issuer du fournisseur (découverte `/.well-known/openid-configuration`) |
| `OIDC_CLIENT_ID` / `OIDC_CLIENT_SECRET` | oui | Client OIDC |
| `OIDC_SCOPES` | non | Défaut `openid profile email` |
| `OIDC_ALLOWED_USERS` | non | Liste séparée par des virgules (email, `preferred_username` ou `sub`) |
| `SESSION_SECRET` | oui | ≥ 32 caractères aléatoires, signe le cookie de session |
| `HOMELAB_ENCRYPTION_KEY` | oui | ≥ 32 caractères aléatoires, chiffre les identifiants stockés. Ne la change pas après coup |
| `DATA_DIR` | non | Dossier de la base SQLite, `/data` dans l'image |

Génère les secrets avec `openssl rand -base64 48`.

## Développement

Node 24 requis (`node:sqlite`).

```bash
npm install
npm run dev
```

Dans un `.env.local`, `HOMELAB_DEV_NO_AUTH=1` saute l'OIDC en local (ignoré en production). Vérifications avant commit : `npx tsc --noEmit`, `npm run lint`, `npm run build`.
