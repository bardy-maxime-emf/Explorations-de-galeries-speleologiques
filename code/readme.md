# Exploration sécurisée de galeries spéléologiques

> Projet Module 306 – Réaliser un projet informatique  
> Rover Phidgets piloté par manette Xbox, avec capteurs (température, humidité, pente) + sonar, pour pré-exploration de grottes.

---

## 1. Contexte du projet

Lors des premières phases d’exploration spéléologique, les galeries peuvent être :

- instables (risques d’effondrement),
- partiellement inondées,
- étroites ou difficiles d’accès (boyaux, zones glissantes),
- globalement dangereuses pour une équipe humaine.

L’objectif est donc d’envoyer **d’abord un robot léger** dans la galerie pour faire une **pré-exploration** et limiter la prise de risque humaine.

Ce robot est un **Rover Phidgets**, piloté à distance via une **manette Xbox**, équipé :

- de moteurs pour se déplacer dans les galeries,
- de capteurs (température, humidité, accéléromètre pour la pente),
- d’un **capteur sonar** pour détecter les obstacles proches,
- d’un éclairage (LED),
- d’un système logiciel pour piloter, visualiser l’état et générer un mini-rapport de mission.

---

## 2. Objectif global

Créer un système complet permettant de **piloter un Rover Phidgets** dans une grotte réelle afin d’effectuer une **pré-exploration sécurisée**, comprenant :

- pilotage intuitif du rover via **manette Xbox**,
- **sonar** pour détecter des obstacles proches (remplace le flux vidéo en V1),
- éclairage puissant monté sur le robot,
- relevés simples :
  - température,
  - humidité,
  - pente approximative (via accéléromètre),
- suivi de la trajectoire (mini “fil d’Ariane”),
- consignation automatique d’un **mini-rapport de mission**.

Le système logiciel doit fonctionner dans un environnement :

- sombre,
- humide,
- irrégulier,
- avec mobilité réduite et connexions potentiellement instables.

---

## 3. Fonctionnalités principales

### 3.1 Pilotage via manette Xbox

- **Joystick gauche** : direction fine (vitesses différentielles roues gauche/droite).
- **Joystick droit** : vitesse avant / arrière + contrôle progressif.
- **Bouton A** : activer / désactiver l’éclairage (LED).
- **Bouton B** : arrêt d’urgence (stop immédiat des moteurs).
- **Boutons latéraux / modes** (optionnel, selon implémentation) :
  - mode vitesse lente,
  - mode vitesse normale.
- **Vibrations de la manette** en cas de :
  - obstacle proche détecté par le sonar,
  - conditions capteurs à risque (pente trop forte, humidité élevée),
  - événement critique (batterie faible, perte de signal, etc. – si disponible).

### 3.2 Capteurs & sonar

- **Capteurs simplifiés** :
  - température,
  - humidité relative,
  - accéléromètre → estimation de la **pente du sol**.
- **Capteur sonar** :
  - mesure de distance à un obstacle,
  - détection “zone libre / obstacle proche”,
  - génération d’événements de risque en cas de distance critique.

### 3.3 Fil d’Ariane & navigation

- Estimation de la **position approximative** du rover :
  - à partir des vitesses des moteurs et du temps,
  - en coordonnant avec les commandes envoyées.
- Construction d’un **fil d’Ariane** :
  - historique des positions (x, y),
  - distance totale parcourue.
- Affichage d’une **mini-carte** 2D (vue de dessus) pour visualiser la trajectoire.

### 3.4 Gestion de mission & rapport

- Démarrer / arrêter une **mission** :
  - génération d’un ID de mission,
  - remise à zéro des compteurs (distance, risques).
- Enregistrement :
  - distance parcourue,
  - événements de risques (capteurs, sonar),
  - horodatage des événements.
- Sauvegarde en **JSON** des données de mission.
- Génération d’un **mini-rapport de mission** (texte/Markdown) incluant :
  - ID mission, durée,
  - distance parcourue,
  - nombre et type de risques détectés,
  - conditions globales (pente max, humidité élevée, etc.).

---

## 4. Architecture logicielle globale

### 4.1 Vue d’ensemble

Le projet suit :

- un découpage **par module fonctionnel** (manette, rover, sonar, capteurs, fil d’Ariane, mission),
- un pattern **MVC** (Model – View – Controller) **dans chaque module**,
- une interface graphique basée sur **JavaFX** + fichiers **FXML** (créés avec Scene Builder).

Arborescence globale :

```text
exploration-rover/
├─ build.gradle ou pom.xml        (optionnel / selon choix de build)
├─ README.md
└─ src/
   ├─ main/
   │  ├─ java/
   │  │  ├─ app/
   │  │  │  └─ main.java
   │  │  ├─ common/
   │  │  ├─ manette/
   │  │  │  ├─ model/
   │  │  │  ├─ view/
   │  │  │  └─ controller/
   │  │  ├─ rover/
   │  │  │  ├─ model/
   │  │  │  ├─ view/
   │  │  │  └─ controller/
   │  │  ├─ sonar/
   │  │  │  ├─ model/
   │  │  │  ├─ view/
   │  │  │  └─ controller/
   │  │  ├─ capteurs/
   │  │  │  ├─ model/
   │  │  │  ├─ view/
   │  │  │  └─ controller/
   │  │  ├─ filariane/
   │  │  │  ├─ model/
   │  │  │  ├─ view/
   │  │  │  └─ controller/
   │  │  └─ mission/
   │  │     ├─ model/
   │  │     ├─ view/
   │  │     └─ controller/
   │  └─ resources/
   │     ├─ ui/
   │     │  ├─ main/MainView.fxml
   │     │  ├─ manette/ManetteDebug.fxml
   │     │  ├─ rover/RoverDebug.fxml
   │     │  ├─ sonar/SonarDebug.fxml
   │     │  ├─ capteurs/CapteursDebug.fxml
   │     │  ├─ filariane/FilArianeDebug.fxml
   │     │  └─ mission/MissionDebug.fxml
   │     └─ css/
   │        └─ styles.css
   └─ test/
      └─ java/
         └─ …
```

⸻

5. Description des modules

5.1 Module manette (pilotage Xbox)

But : lire la manette Xbox, maintenir son état, générer des événements pour les autres modules, gérer les vibrations.
	•	manette/model
	•	Classe(s) pour l’état de la manette :
	•	joysticks (gauche/droite, X/Y),
	•	boutons (A, B, LB, RB, etc.),
	•	modes (lent, normal).
	•	manette/view
	•	Vue de debug (ex. ManetteDebugView ou contrôleurs JavaFX) pour afficher l’état de la manette.
	•	manette/controller
	•	Lecture en continu de la manette (librairie ou API),
	•	application de la dead-zone sur les joysticks,
	•	génération d’événements (ex : onBoutonA, onBoutonB, onJoystickChanged),
	•	gestion des vibrations :
	•	vibration faible (notification),
	•	vibration d’alerte (risque).

5.2 Module rover (moteurs & connexion)

But : gérer la communication avec le Rover Phidgets et le contrôle des moteurs.
	•	rover/model
	•	État connexion (connecté / déconnecté),
	•	vitesses des roues,
	•	mode actuel (stop, avancer, reculer, tourner).
	•	rover/view
	•	Vue de debug : affichage des vitesses, des états, boutons de test.
	•	rover/controller
	•	Connexion / déconnexion au rover,
	•	méthode setMoteurs(vG, vD),
	•	commandes de base :
	•	avancer,
	•	reculer,
	•	tourner sur place,
	•	arrêt d’urgence,
	•	génération d’événements :
	•	onRoverConnected,
	•	onRoverDisconnected,
	•	onVitessesChanged.

5.3 Module sonar (détection obstacle)

But : mesurer la distance à un obstacle et générer des alertes de proximité.
	•	sonar/model
	•	distance mesurée,
	•	qualité du signal (optionnel),
	•	état “zone libre / obstacle proche”.
	•	sonar/view
	•	Vue de debug : affichage de la distance, indicateur couleur.
	•	sonar/controller
	•	démarrage / arrêt des mesures périodiques,
	•	détection de distance critique (seuil),
	•	génération d’événements de risque :
	•	obstacle proche,
	•	éventuellement obstacles multiples.

5.4 Module capteurs (température, humidité, pente)

But : lire les capteurs et marquer les conditions à risque.
	•	capteurs/model
	•	température,
	•	humidité,
	•	vecteur accéléromètre,
	•	pente calculée.
	•	capteurs/view
	•	Vue de debug des valeurs des capteurs.
	•	capteurs/controller
	•	lecture périodique des valeurs,
	•	calcul de la pente à partir de l’accéléromètre,
	•	définition des seuils de risque (pente, humidité),
	•	génération d’événements onRisqueCapteur(type, valeur).

5.5 Module filariane (navigation & trajectoire)

But : suivre la position approx. du rover et tracer sa trajectoire.
	•	filariane/model
	•	position (x, y),
	•	angle,
	•	historique de positions,
	•	distance totale.
	•	filariane/view
	•	mini-carte 2D (vue de dessus),
	•	bouton “Reset trajectoire”.
	•	filariane/controller
	•	s’abonne aux changements de vitesses moteurs,
	•	estime le déplacement en fonction du temps et des vitesses,
	•	met à jour la trajectoire.

5.6 Module mission (gestion & rapport)

But : gérer le cycle de vie d’une mission et produire un rapport.
	•	mission/model
	•	ID de mission,
	•	état (en cours / terminée),
	•	temps de début / fin,
	•	distance totale,
	•	liste de risques/événements.
	•	mission/view
	•	Vue de debug :
	•	démarrer / arrêter mission,
	•	voir les compteurs.
	•	mission/controller
	•	démarre et arrête une mission,
	•	s’abonne aux événements :
	•	capteurs,
	•	sonar,
	•	fil d’Ariane (distance),
	•	enregistre les événements dans le model,
	•	sauvegarde les données en JSON,
	•	génère le mini-rapport.

⸻

6. Pattern MVC dans ce projet

6.1 Model
	•	Représente l’état et la logique métier locale d’un module.
	•	Ne connaît ni JavaFX ni les FXML directement.
	•	Exemple : ManetteState, RoverState, CapteursData, MissionData.

6.2 View
	•	Partie liée à l’affichage.
	•	S’appuie sur les fichiers FXML (Scene Builder) et les contrôleurs JavaFX.
	•	Peut avoir des classes utilitaires pour adapter les models à la vue.

6.3 Controller
	•	Relie les models et les vues.
	•	Gère :
	•	les entrées utilisateur (manette),
	•	les entrées matérielles (capteurs, sonar, rover),
	•	la mise à jour des models,
	•	la notification des vues.

⸻

7. Gestion des événements (common)

Le package common joue un rôle important de colle entre modules :
	•	EventBus (simple) pour diffuser les événements :
	•	onBoutonA,
	•	onBoutonB,
	•	onRisqueCapteur,
	•	onObstacleProche,
	•	onMissionStart,
	•	etc.
	•	Classe(s) Config pour :
	•	les seuils (pente, humidité, distance sonar),
	•	les fréquences de rafraîchissement.

⸻

8. Mise en place du projet (création des dossiers)

Sur macOS, à partir d’un terminal (par exemple celui de VS Code) :

mkdir -p exploration-rover
cd exploration-rover

mkdir -p src/main/java
mkdir -p src/main/resources
mkdir -p src/test/java

mkdir -p src/main/java/app
mkdir -p src/main/java/common

for module in manette rover sonar capteurs filariane mission; do
  mkdir -p "src/main/java/$module/model" \
           "src/main/java/$module/view" \
           "src/main/java/$module/controller"
done

for view in main manette rover sonar capteurs filariane mission; do
  mkdir -p "src/main/resources/ui/$view"
done

mkdir -p src/main/resources/css

Fichier d’entrée Java :
src/main/java/app/main.java

⸻

9. Intégration globale (UI principale)

La vue principale JavaFX (l’équivalent du “index global”) est décrite dans :
	•	src/main/resources/ui/main/MainView.fxml

Elle contient par exemple :
	•	un header :
	•	état manette,
	•	état rover,
	•	info sonar,
	•	résumé capteurs,
	•	état mission (en cours / arrêtée).
	•	des onglets :
	•	Pilotage,
	•	Sonar,
	•	Capteurs,
	•	Fil d’Ariane,
	•	Mission.

Les contrôleurs JavaFX de cette vue se trouvent dans le package app ou mission/view/controller selon l’organisation retenue.

⸻

10. Tests & calibration

10.1 Tests unitaires & fonctionnels
	•	tests unitaires (optionnel) dans src/test/java,
	•	tests fonctionnels par module :
	•	manette seule (debug manette),
	•	rover seul,
	•	sonar seul,
	•	capteurs seuls,
	•	fil d’Ariane seul,
	•	mission seule.

10.2 Calibration
	•	pente (accéléromètre),
	•	seuil d’humidité à risque,
	•	distance critique sonar,
	•	vérification que les vibrations de la manette correspondent aux bons événements.

⸻

11. Travail d’équipe & gestion de projet
	•	Utilisation de Git + GitHub :
	•	une issue par tâche (selon la liste que tu as déjà),
	•	branches par fonctionnalité,
	•	pull requests et revues de code si possible.
	•	Méthodologie SCRUM / Agile :
	•	backlog (issues GitHub),
	•	sprints (ex. 1 semaine),
	•	revues rapides en fin de session.

⸻

12. Documentation à produire (niveau école)

Même si le code est la base du projet, la documentation demandée par l’école doit être produite à part (dans un dossier docs/ par exemple) :
	•	planification,
	•	journal de travail,
	•	documentation d’analyse,
	•	documentation de réalisation,
	•	Web Summary.