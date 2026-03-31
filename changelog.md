## [0.1.6] - 2026-03-31
### Ajouté
- **Points d'achats agrégés** : Marqueurs d'achats/ventes désormais affichés sur les graphiques de Tendances globales.
 
### Corrigé
- **Popup de variations de prix** : Augmentation de la priorité des notifications pour garantir l'affichage en bannière sur Android 13+.
- **Seuil d'alerte** : Ajustement de la sensibilité (>2.95% instead of 3.0%) et réduction de l'intervalle de répétition à 6h.

## [0.1.5] - 2026-03-31
### Ajouté
- **Page de Tendances du Portefeuille** : Accès direct en cliquant sur le bandeau supérieur pour visualiser l'historique complet de la valeur du portefeuille.
- **Graphiques d'Agrégation** : Deux graphiques distincts pour suivre séparément l'évolution des **Actifs financiers** et des **Métaux précieux**.
- **Filtres Temporels Étendus** : Support de nouvelles périodes d'analyse (**3j, 15j, 6m**) disponibles sur tous les graphiques de l'application.

## [0.1.3] - 2026-03-30
### Ajouté
- **Séparation de l'Or Physique** : Le bandeau du haut (App) et le **Widget** affichent désormais distinctement la valeur des actifs financiers et celle de l'or physique (pièces, lingotins, lingots), avec leurs variations respectives.

## [0.1.2] - 2026-03-25
### Ajouté
- **Option de Tri 24h** : Par défaut, vos actifs listés dans "Mes actifs" sont désormais triés d'office par la performance globale descendante sur 24h.

### Corrigé
- **Alertes de prix récalcitrantes** : Le moteur en arrière-plan vérifie désormais de manière plus agressive s'il y a eu une variation supérieure à 3% afin de ne plus louper un mouvement du marché.
- **Icône de Notifications** : L'icône de la notification qui pouvait faire défaut sur certains téléphones (Android 8 et +) a été stabilisée.
## [0.1.1] - 2026-03-13
### Ajouté
- **Widget de bureau (2x1)** : Affichez la valeur totale de votre portefeuille et la performance du jour directement sur votre écran d'accueil.
- **Rafraîchissement interactif** : Un appui sur le widget force la mise à jour des prix. Mise à jour automatique toutes les 30 minutes.

### Corrigé
- **Alertes de prix (notifications)** : Correction de la logique de détection des variations de prix (>3%). Les notifications sont désormais plus réactives et prioritaires (bannière, son, vibration).
- **Formatage du widget** : Ajustement du symbole € après le montant et gestion propre des signes +/- sur les gains.

## [0.1.0] - 2026-03-12
### Ajouté
- Structure de base pour le passage en version Beta.
- Nettoyage des dépendances.

## [0.0.38] - 2026-03-11
### Ajouté
- **Prix unitaire sur les cartes** : Le prix pour 1 unité est affiché à droite, sous le montant total, sur toutes les cartes d'actifs (ETF, actions, métaux, crypto).

### Corrigé
- **Graphique toujours affiché** : Correction d'une race condition qui empêchait le graphique de s'afficher 1 fois sur 2 en naviguant vers un actif.
- **Graphique des métaux (Napoléon, lingots)** : Correction du fallback Yahoo Finance — utilisation de `GC=F` au lieu de `XAUEUR=X` (qui ne supportait pas les longues plages historiques), et application correcte du multiplicateur de poids.
- **Noms longs** : Les noms d'actifs trop longs sont maintenant tronqués avec `…` sur les cartes, sans déborder sur le montant.

## [0.0.37] - 2026-03-10
### Corrigé
- **Calcul des gains critique** : Correction de l'inversion du taux EUR/USD (division au lieu de multiplication).
- **Unités des métaux** : Prix désormais calculés par lingot (1kg, 100g) et par pièce (Napoléon) au lieu du gramme simple.
- **Conversion multi-devises** : Support des actifs en GBp (Pences), GBP et CHF avec conversion vers l'Euro.
- **Stabilité du PRU** : Tri chronologique strict (Achats avant Ventes sur même timestamp) pour des gains réalisés exacts.
- **Performance du jour** : Nouveau moteur prenant en compte les achats récents pour ne pas fausser les gains sur 24h.
- **Fallback Crypto** : Récupération des prix via Yahoo si CoinGecko est manquant ou saturé.

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
