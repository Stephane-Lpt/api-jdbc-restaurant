import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.DriverManager;
import java.util.InputMismatchException;
import java.util.Properties;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws SQLException {
        // On charge les paramètres de connexion à la base de données
        Properties config = DBConfig.loadConfig("resources/db.conf");
        String hostname = config.getProperty("hostname");
        String port = config.getProperty("port");
        String database = config.getProperty("database");
        String dbLogin = config.getProperty("login");
        String dbPassword = config.getProperty("password");

        String url = "jdbc:oracle:thin:@" + hostname + ":" + port + ":" + database;

        // Connexion à la base de données
        Connection conn = DriverManager.getConnection(url, dbLogin, dbPassword);
        conn.setAutoCommit(false);

        Scanner sc = new Scanner(System.in);

        // Exemple de login pour un gestionnaire
        // email = "user1@mail.com";
        // password = "serveur0";

        // Exemple de login pour un serveur
        // email : user2@mail.com
        // password : serveur1

        /* ---- Authentification ---- */
        System.out.println("Veuillez entrer votre login :");
        String email = sc.nextLine();
        email = email.replaceAll("[\r\n]+", "");

        System.out.println("Veuillez entrer votre mot de passe :");
        String password = sc.nextLine();
        password = password.replaceAll("[\r\n]+", "");


        // On vérifie si le login et le mot de passe du serveur sont corrects
        String sql = "SELECT * FROM serveur WHERE email = ? AND passwd = ?";
        PreparedStatement pstmt = conn.prepareStatement(sql);

        pstmt.setString(1, email);
        pstmt.setString(2, password);
        ResultSet rs = pstmt.executeQuery();

        // Si la requête retourne un résultat, alors le login est réussi
        if (rs.next()) {
            String numserv = rs.getString("numserv");
            String nom = rs.getString("nomserv");
            String grade = rs.getString("grade");

            Serveur serveur = null;
            // On crée un objet Serveur ou Gestionnaire en fonction du grade
            if (grade.equals("gestionnaire")) {
                serveur = new Gestionnaire(numserv, email, nom);
                System.out.println("Bonjour " + serveur.getNom() + ", vous êtes connecté en tant que gestionnaire.");


            } else {
                serveur = new Serveur(numserv, email, nom);
                System.out.println("Bonjour " + serveur.getNom() + ", vous êtes connecté en tant que serveur.");
            }

            /* ---- Affichage du menu ---- */

            System.out.println(serveur.getMenu());
            int choix = sc.nextInt();
            sc.nextLine(); // On doit consommer le retour à la ligne après le nextInt pour éviter qu'il ne soit consommé par le nextLine suivant et on aura une entrée vide.
            while (choix != 0){
                try {
                    String date = null;
                    int nbPers;
                    int numtab;
                    switch (choix) {
                        case 1:
                            System.out.println("Veuillez entrer la date de réservation (format : yyyy-mm-dd hh:mm:ss) :");
                            date = sc.nextLine();

                            System.out.println("Veuillez entrer le nombre de personnes :");
                            nbPers = sc.nextInt();

                            serveur.consulterTablesDispos(conn, date, nbPers, true);
                            break;
                        case 2:
                            System.out.println("Veuillez entrer la date de réservation (format : yyyy-mm-dd hh:mm:ss) :");
                            date = sc.nextLine();

                            System.out.println("Veuillez entrer le nombre de personnes :");
                            nbPers = sc.nextInt();

                            System.out.println("Veuillez entrer le numéro de la table :");
                            numtab = sc.nextInt();

                            serveur.reserverTable(conn, date, nbPers, numtab);
                            break;
                        case 3:
                            serveur.consulterPlatsDispos(conn);
                            break;
                        case 4:
                            System.out.println("Veuillez entrer le numéro de la réservation :");
                            int numres = sc.nextInt();

                            System.out.println("Veuillez entrer le numéro du plat :");
                            int numplat = sc.nextInt();

                            System.out.println("Veuillez entrer la quantité :");
                            int quantite = sc.nextInt();

                            serveur.commanderPlat(conn, numres, numplat, quantite);
                            break;
                        case 5:
                            ((Gestionnaire) serveur).consulterAffectations(conn);
                            break;
                        case 6:
                            System.out.println("Veuillez entrer le numéro du serveur :");
                            int numservAffectation = sc.nextInt();

                            System.out.println("Veuillez entrer le numéro de la table :");
                            numtab = sc.nextInt();
                            ((Gestionnaire) serveur).affecterServeurTable(conn, numtab, numservAffectation);
                            break;
                        case 7:
                            System.out.println("Veuillez entrer le numéro de la réservation :");
                            numres = sc.nextInt();
                            ((Gestionnaire) serveur).calculerMontantTotalCommandeEtMajReservation(conn, numres);
                            break;
                        default:
                            System.err.println("Choix invalide. Veillez entrer un chiffre entre 0 et 7.");
                    }
                } catch (IllegalArgumentException | InputMismatchException e){
                    System.err.println("Veuillez vérifier vos entrées (leur type et leur format)");
                } catch (SQLException e){
                    System.err.println("Erreur de communication avec la base de données");
                } catch (Exception e){
                    System.err.println("Vous n'avez pas les permissions d'utiliser cette fonctionnalité");
                }

                try {
                    System.out.println(serveur.getMenu());
                    sc.nextLine();
                    choix = sc.nextInt();
                    sc.nextLine(); // On doit consommer le retour à la ligne après le nextInt pour éviter qu'il ne soit consommé par le nextLine suivant et on aura donc une entrée vide.
                } catch (InputMismatchException e){
                    System.err.println("Votre choix doit être un chiffre allant de 0 à 7 selon votre rôle.");
                }
            }
        }
        else {
            System.err.println("Login ou mot de passe incorrect");
        }

        System.out.println("Fermeture de la fenêtre...");
        // Fermeture la connexion
        conn.close();
        



    }
}
