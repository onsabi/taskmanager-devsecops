# Task Manager – Mini-projet DevSecOps

Application Java / Spring Boot de gestion des tâches, avec une chaîne CI/CD sécurisée :
**GitHub → Jenkins → Maven → SonarQube → OWASP Dependency-Check → Docker → Trivy → Docker Hub → Kubernetes (Minikube) → OWASP ZAP**.

---

## 1. Architecture

```
Développeur ── push ──► GitHub ── webhook ──► Jenkins
                                              │
            ┌─────────────────────────────────┤
            ▼                                 ▼
   Maven build + tests (JUnit / JaCoCo)   SonarQube (SAST, Quality Gate)
            │
            ▼
   OWASP Dependency-Check (CVE des dépendances)
            │
            ▼
   Docker build (Dockerfile multi-étapes, utilisateur non-root)
            │
            ▼
   Trivy (scan de l'image, échec sur CRITICAL)
            │
            ▼
   Docker Hub (push de l'image validée)
            │
            ▼
   Kubernetes / Minikube (Deployment + Service, probes, ressources)
            │
            ▼
   OWASP ZAP baseline (DAST sur l'application déployée)
```

### API REST

| Méthode | Route            | Description                    | Réponse        |
|---------|------------------|--------------------------------|----------------|
| GET     | `/api/tasks`     | Liste des tâches               | 200            |
| GET     | `/api/tasks/{id}`| Détail d'une tâche             | 200 / 404      |
| POST    | `/api/tasks`     | Création d'une tâche           | 201 / 400      |
| PUT     | `/api/tasks/{id}`| Modification d'une tâche       | 200 / 400 / 404|
| DELETE  | `/api/tasks/{id}`| Suppression d'une tâche        | 204 / 404      |
| GET     | `/actuator/health/liveness` | Probe de vivacité   | 200            |
| GET     | `/actuator/health/readiness`| Probe de disponibilité | 200         |

Exemple de corps de requête :

```json
{ "title": "Rédiger le rapport", "description": "Chapitre sécurité", "done": false }
```

---

## 2. Prérequis

- Ubuntu / Linux
- JDK 21, Maven 3.9+
- Docker
- Minikube et kubectl
- Jenkins (avec les plugins : Pipeline, Git, GitHub, SonarQube Scanner, OWASP Dependency-Check, HTML Publisher, JaCoCo, JUnit, Docker Pipeline, Kubernetes CLI)
- Un compte Docker Hub et un compte GitHub
- SonarQube (conteneur Docker recommandé) et une clé NVD (https://nvd.nist.gov/developers/request-an-api-key)

---

## 3. Exécution locale

```bash
# Compiler, tester, générer les rapports de couverture
mvn clean verify

# Lancer l'application
mvn spring-boot:run

# Tester l'API
curl -s -X POST localhost:8080/api/tasks -H 'Content-Type: application/json' \
     -d '{"title":"Test","description":"","done":false}'
curl -s localhost:8080/api/tasks
```

## 4. Conteneurisation

```bash
docker build -t onsabi/taskmanager:1.0.0 .
docker run --rm -p 8080:8080 onsabi/taskmanager:1.0.0
curl -s localhost:8080/actuator/health/readiness
```

Choix du Dockerfile :
- **Build multi-étapes** : Maven compile dans une image `maven`, seul le JAR est copié dans l'image finale.
- **Image d'exécution minimale** : `eclipse-temurin:21-jre-alpine` (JRE seul, sans Maven ni JDK).
- **Utilisateur non-root** (UID 10001) et système de fichiers en lecture seule côté Kubernetes.
- **Limites mémoire respectées** par la JVM (`MaxRAMPercentage`).

## 5. Déploiement sur Minikube

```bash
minikube start --driver=docker
minikube image load onsabi/taskmanager:1.0.0      # ou utiliser l'image Docker Hub
kubectl apply -f k8s/deployment.yaml -f k8s/service.yaml
kubectl get pods -l app=taskmanager
minikube service taskmanager --url
```

Le `Deployment` définit :
- 2 réplicas, ressources `requests` / `limits` (CPU et mémoire) ;
- une **readinessProbe** et une **livenessProbe** sur `/actuator/health/*` ;
- un `securityContext` restrictif : `runAsNonRoot`, `readOnlyRootFilesystem`, `allowPrivilegeEscalation: false`, `capabilities: drop ALL`, profil seccomp `RuntimeDefault`.

Le `Service` est de type `NodePort` (port 30080) pour l'accès depuis l'hôte.

---

## 6. Pipeline Jenkins

Le fichier `Jenkinsfile` est versionné avec le projet. Étapes :

| # | Étape | Outil | Effet en cas d'échec |
|---|-------|-------|----------------------|
| 1 | Checkout | Git | — |
| 2 | Build & tests unitaires | Maven, JUnit, JaCoCo | Pipeline arrêté |
| 3 | Analyse du code | SonarQube | — |
| 4 | Quality Gate | SonarQube | Pipeline arrêté |
| 5 | Analyse des dépendances | OWASP Dependency-Check | Pipeline arrêté si CVSS ≥ 7 |
| 6 | Construction Docker | Docker | Pipeline arrêté |
| 7 | Scan de l'image | Trivy | Pipeline arrêté si CRITICAL corrigeable |
| 8 | Publication | Docker Hub | — (uniquement `main` / `develop`) |
| 9 | Déploiement | kubectl / Minikube | Pipeline arrêté (uniquement `main`) |
| 10 | Test dynamique | OWASP ZAP | Non bloquant : rapport à analyser |

### Credentials Jenkins à créer

| ID | Type | Usage |
|----|------|-------|
| `dockerhub-creds` | Username + password | Connexion et push Docker Hub |
| `sonar-token` | Secret text | Analyse SonarQube |
| `nvd-api-key` | Secret text | Mises à jour de la base NVD (Dependency-Check) |
| `kubeconfig` | Secret file | Déploiement sur Minikube |

Le serveur SonarQube doit être déclaré dans Jenkins sous le nom **`SonarQube`**, et l'outil Maven sous le nom **`Maven-3.9`**.

Le webhook GitHub pointe vers `http://<jenkins>/github-webhook/` (type *application/json*, événement *push*).

---

## 7. Politique de sécurité

### 7.1 Quality Gate SonarQube
Le Quality Gate bloque la livraison si, sur le code nouveau :
- au moins une **vulnérabilité** ou un **hotspot de sécurité** non revu est détecté ;
- la **couverture** des tests est inférieure au seuil défini dans le Quality Gate ;
- la **duplication** ou le **nombre de bugs** dépasse le seuil ;
- le **rating de maintenabilité / fiabilité / sécurité** est inférieur à A.

### 7.2 Dépendances (OWASP Dependency-Check)
- Une CVE de **CVSS ≥ 7** (HIGH / CRITICAL) fait échouer le build (`failBuildOnCVSS` dans le `pom.xml`).
- Une dépendance vulnérable doit être mise à jour ; si aucun correctif n'existe, elle est documentée et acceptée explicitement.

### 7.3 Image Docker (Trivy)
| Sévérité | Comportement | Justification |
|----------|--------------|---------------|
| CRITICAL (avec correctif disponible) | **Bloque** la publication | Risque d'exploitation immédiat |
| HIGH | Rapport archivé, à corriger dans le sprint | Risque significatif mais non bloquant |
| Sans correctif (`--ignore-unfixed`) | Ignoré | Rien à corriger côté projet |

Les CVE acceptées sont listées dans un fichier `.trivyignore` avec une justification et une date de réexamen.

### 7.4 Test dynamique (OWASP ZAP)
Le scan baseline est exécuté contre l'application déployée. Il identifie notamment les en-têtes HTTP manquants, les fuites d'informations et la configuration des cookies. Un scan ZAP **ne garantit pas l'absence de vulnérabilités** : il complète les autres contrôles. Les alertes Medium et High sont analysées dans le rapport technique.

### 7.5 Mesures de sécurité appliquées à l'application
- Validation des entrées (`@NotBlank`, `@Size`) ;
- aucune trace d'exception ni message interne renvoyé au client (`server.error.include-*=never`) ;
- conteneur non-root, système de fichiers en lecture seule, capacités Linux supprimées ;
- secrets jamais écrits dans le dépôt : ils sont gérés dans les credentials Jenkins.

---

## 8. Rapports produits

| Outil | Rapport | Emplacement |
|-------|---------|-------------|
| JUnit | Résultats des tests | Onglet *Test Result* Jenkins |
| JaCoCo | Couverture de code | `target/site/jacoco/` |
| SonarQube | Analyse qualité et sécurité | Serveur SonarQube, projet `taskmanager-devsecops` |
| Dependency-Check | CVE des dépendances | Onglet *OWASP Dependency-Check* / `target/dependency-check-report.html` |
| Trivy | Vulnérabilités de l'image | `reports/trivy-report.txt` (archivé) |
| OWASP ZAP | Alertes DAST | `reports/zap-report.html` et `zap-report.json` (archivés) |

---

## 9. Organisation Git

- Branche `main` : version stable, déclenche le déploiement complet.
- Branche `develop` : intégration, déclenche le build, les analyses et la publication Docker Hub.
- Branches `feature/<nom>` : développement des fonctionnalités, fusionnées dans `develop` par Pull Request.

Convention de commits : messages explicites à l'impératif (`Ajoute la validation du titre`, `Corrige la suppression d'une tâche inexistante`).

---

## 10. Limites connues

- Stockage **en mémoire** : les données sont perdues au redémarrage (choix pédagogique ; à remplacer par une base de données en production).
- Pas d'authentification sur l'API : à ajouter (Spring Security) avant toute exposition réelle.
- Le scan ZAP est un **baseline** (passif) ; un scan *full* actif nécessite un environnement de test dédié.
