# Journal des modifications (Changelog) - Valoria

## [0.0.36] - 2026-03-09
### Corrigé
- **Stabilisation des Graphiques** : Système de fallback automatique vers Yahoo Finance si CoinGecko est saturé (erreur 429).
- **Fiabilité 24h/7j** : Affichage garanti même hors sessions de trading avec extension de la plage de données.
- **Support Crypto Étendu** : Meilleure identification des cryptos via Yahoo (tickers BTC-EUR, etc.).

## [0.0.35] - 2026-03-09
### Corrigé
- **Correctif Yahoo Finance** : Migration vers l'endpoint `v8/chart` (le précédent `v7/quote` étant devenu instable/bloqué).
- **Cache de données** : Optimisation des appels API via un cache de graphiques en mémoire de 15 minutes.
- **Métaux précieux** : Support de l'or et de l'argent via tickers de secours (Gold/Silver Futures) en cas de panne des devises directes.

## [0.0.33] - 2026-03-09
### Ajouté
- **Système d'alertes de prix** : Notifications automatiques si un actif varie de plus de 3% sur 24h.
- **Surveillance en arrière-plan** : Actualisation automatique des prix toutes les 2 heures via WorkManager.
- **Gestion intelligente des notifications** : Limitation à une alerte par jour et par actif pour éviter le spam.
- **Paramètres d'alertes** : Nouveau switch dans les réglages pour activer/désactiver les notifications.
- **Permissions Android 13+** : Demande dynamique de l'autorisation d'envoi de notifications lors de l'activation.

## [0.0.32] - 2026-03-09
### Ajouté
- **Filtres temporels du graphique** : Sélection de périodes (24h, 7j, 1m, 1an, 5ans, Tout).
- **Historique étendu** : Le mode "Tout" affiche désormais les données depuis 2015.
- **Marqueurs de transactions** : Points visuels sur la courbe (Vert pour les achats, Rouge pour les ventes).
- **Format de date amélioré** : Ajout de l'année au format XX lors de la navigation sur le graphique.
- **Fallback intelligent 24h** : Affichage des derniers points de trading même si les marchés sont fermés (week-end/fériés).

## [0.0.31] - Avant
- Structure de base du portefeuille.
- Import/Export de fichiers .val.
- Synchronisation avec dossier local.
