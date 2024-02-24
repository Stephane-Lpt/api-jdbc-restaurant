# tp-bd-s4

Travail demandé
Le but du projet est de développer une application Java dotée d’une interface
textuelle (très simple) pour exécuter ses fonctionnalités, et accédant à une base de
données via l’API JDBC en mode transactionnel pour éviter des situations
d’incohérence.
Le travail se présente comme suit :

• Créer les tables ainsi que le jeu de données.
• L’application doit implémenter les fonctionnalités suivantes :
1. Un module de connexion pour les serveurs (login/mdp) du restaurant.
2. Un simple serveur doit pouvoir réaliser les opérations suivantes :
a. Consulter les tables disponibles pour une date et heure données.
b. Réserver une table pour une date et heure données.
c. Consulter les plats disponibles pour une éventuelle commande.
d. Commander des plats.
3. En plus des opérations d’un simple serveur, un gestionnaire doit pouvoir
réaliser d’autres opérations à savoir :
a. Consulter les affectations des serveurs.
b. Affecter des serveurs à des tables.
c. Calculer le montant total d’une réservation consommée (numéro de
réservation) et mettre à jour la table RESERVATION pour l’encaissement.
