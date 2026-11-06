# Homelab Web

Tableau de bord web de Homelab, pensé pour le PC : importe la sauvegarde de l'app mobile et suis tous tes services depuis le navigateur.

- **Import / export** du fichier `.homelab` de l'app (même chiffrement AES-256-GCM, compatible dans les deux sens). Le déchiffrement de l'import se fait dans le navigateur, le mot de passe n'est jamais envoyé au serveur.
- **Suivi en direct** : statut, latence et métrique clé par service (Radarr, Sonarr, qBittorrent, Gluetun, Proxmox, PeaNUT, SafeLine, AMP, Uptime Kuma…), rafraîchi chaque minute.
- **Connexion OIDC** (n'importe quel fournisseur avec découverte standard), avec liste d'utilisateurs autorisés optionnelle.
- Les identifiants des services sont stockés chiffrés dans SQLite (`/data/homelab.db`).

## Lancer avec Docker

```bash
cp .env.example .env        # puis remplis les valeurs
docker compose -f docker-compose.example.yml up -d --build
```

La base SQLite vit dans le volume Docker `homelab-data`, rien à créer sur l'hôte. Pour la garder dans un dossier de l'hôte, remplace le volume par `./data:/data` : au démarrage, le conteneur rend ce dossier accessible puis fait tourner le serveur en utilisateur non privilégié `node`.

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

## Crédits

Logos des services : [selfh.st/icons](https://github.com/selfhst/icons) (les mêmes que l'app mobile), embarqués dans `public/icons`. Chaque logo reste la marque de son projet.
