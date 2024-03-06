import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Classe Serveur qui réalise les opérations courantes d'un serveur
 */
public class Serveur {
    /**
     * numéro du serveur
     */
    private String numserv;
    /**
     * email du serveur
     */
    private String email;
    /**
     * nom du serveur
     */
    private String nom;

    /**
     * Constructeur de la classe Serveur
     * @param numserv numéro du serveur
     * @param email email du serveur
     * @param nom nom du serveur
     */
    public Serveur(String numserv, String email, String nom) {
        this.numserv = numserv;
        this.email = email;
        this.nom = nom;
    }


    // Je pars du principe, que dans un restaurant, il y a peu de serveurs (<10) dans beaucoup de cas
    // Donc, je privilégie le verrouilage optimiste pour la réservation d'une table. Je ne mets donc pas de verrou dès la consultation (verrouillage pessimiste)
    // Enfin, je pars du postulat que la durée moyenne d'un repas est de 2 heures, donc je prends une marge de 2 heures avant et après la date de réservation pour vérifier la disponibilité des tables.

    /**
     *
     * @param conn connexion à la base de données (que je mets volontairement en paramètre plutôt qu'en attribut si jamais on veut changer de connexion)
     * @param date date de réservation
     * @param nbPers nombre de personnes
     * @param affichage va permettre de savoir si la méthode va être une méthode intermédiaire ou pas. Dans un cas, on print les tables disponibles, dans l'autre, non.
     * @return la liste des numéros des tables disponibles pour la date et le nombre de personnes donnés
     * @throws SQLException si une erreur survient lors de la requête SQL
     */
    public List<Integer> consulterTablesDispos(Connection conn, String date, int nbPers, boolean affichage) throws SQLException{
        conn.setAutoCommit(false);

        // Convertir la date en Timestamp
        Timestamp timestamp = Timestamp.valueOf(date);

        // Calculer les timestamps pour 2 heures avant et après
        Timestamp twoHoursBefore = new Timestamp(timestamp.getTime() - 2 * 60 * 60 * 1000);
        Timestamp twoHoursAfter = new Timestamp(timestamp.getTime() + 2 * 60 * 60 * 1000);

        String sql = "SELECT numtab FROM tabl WHERE nbplace >= ? AND numtab NOT IN (SELECT numtab FROM reservation WHERE datres BETWEEN ? AND ?)";
        PreparedStatement pstmt = conn.prepareStatement(sql);

        pstmt.setInt(1, nbPers);
        pstmt.setTimestamp(2, twoHoursBefore);
        pstmt.setTimestamp(3, twoHoursAfter);

        ResultSet rs = pstmt.executeQuery();

        // On stocke les numéros des tables disponibles dans une liste
        ArrayList<Integer> numsTablesDispos = new ArrayList<>();
        while (rs.next()) {
            numsTablesDispos.add(rs.getInt("numtab"));
            if(affichage) System.out.println("Table disponible : " + rs.getInt("numtab"));
        }

        rs.close();
        pstmt.close();

        return numsTablesDispos;
    }

    /**
     * Réserver une table pour une date donnée
     * Je privilégie le verrouillage pessimiste pour la réservation d'une table (ce qui est possible car on peut vérifier si une réservation a été faite par quelqu'un d'autre avec la clé primaire numres)
     * La probabilité qu'il y ait une collision est faible car la probabilité que l'insert d'un autre serveur se fasse entre la vérification (consultation) et l'insertion est faible notamment car le nombre de serveurs dans un restaurant est faible théoriquement (pas plus de 10-20 en général)
     * @param conn connexion à la base de données (que je mets volontairement en paramètre plutôt qu'en attribut si jamais on veut changer de connexion)
     * @param date date de réservation
     * @param numtab numéro de la table
     * @return true si la réservation a été effectuée avec succès, false sinon
     */
    public boolean reserverTable(Connection conn, String date, int nbPers, int numtab) {
        try {
            conn.setAutoCommit(false);

            // On vérifie si la table est disponible pour la date donnée
            if(!consulterTablesDispos(conn, date, nbPers,false).contains(numtab)) {
                System.err.println("Soit il n'y a pas assez de places à cette table, soit la table " + numtab + " n'est pas disponible pour la date " + date + ".");
                System.err.println("On ne prend qu'une réservation qu'à intervalle de 2h. (Repas + nettoyage + dressage)");
                return false;
            }

            // Pas besoin de spécifier un numres (on utilise IDENTITY pour l'auto-incrémentation) pour faire l'équivalent de AUTO_INCREMENT avec une BD Oracle
            String sql = "INSERT INTO reservation (numtab, datres, nbpers) VALUES (?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(sql);

            pstmt.setInt(1, numtab);
            pstmt.setTimestamp(2, Timestamp.valueOf(date));
            pstmt.setInt(3, nbPers);

            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected > 0) {
                System.out.println("La table " + numtab + " a été réservée avec succès pour la date " + date + ".");
            } else {
                System.out.println("Désolé, la réservation de la table " + numtab + " pour la date " + date + " a échoué.");
            }

            conn.commit(); // Commit explicite pour valider la réservation (l'insert donc)
            pstmt.close();
            return true;
        } catch (SQLException e) {
            try {
                conn.rollback(); // Rollback explicite pour annuler la réservation et relâcher le verrou sur la table tabl
            } catch (SQLException e1) {
                e1.printStackTrace();
            }
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Consulter les plats disponibles pour une éventuelle commande
     * @param conn connexion à la base de données (que je mets volontairement en paramètre plutôt qu'en attribut si jamais on veut changer de connexion)
     */
    public void consulterPlatsDispos(Connection conn) {
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM plat WHERE qteservie > 0");

            while(rs.next()) {
                System.out.println("Numéro du plat: " + rs.getInt("numplat") + ", Nom du plat: " + rs.getString("libelle") + ", Type: " + rs.getString("type") + ", Prix unitaire : " + rs.getDouble("prixunit") + ", Quantité servie: " + rs.getInt("qteservie"));
            }
        } catch(SQLException e) {
            e.printStackTrace();
        }
    }

    // Verouillage pessimiste pour commander un plat, car nous ne pouvons pas identifier les commandes (lignes) ajoutées par quelqu'un d'autre (pas de clé primaire numcommande, seulement des clés étrangères)
    public void commanderPlat(Connection conn, int numres, int numplat, int quantite) {
        try {
            conn.setAutoCommit(false);

            // Si le montant de la commande est déjà calculé (ce qui indique que la réservation a été déjà encaissée), on ne peut pas commander de nouveaux plats
            PreparedStatement pstmtCheck = conn.prepareStatement("SELECT montcom FROM reservation WHERE numres = ?");
            pstmtCheck.setInt(1, numres);
            ResultSet rs = pstmtCheck.executeQuery();

            if(rs.next() && rs.getDouble("montcom") != 0) {
                System.err.println("Cette réservation a été cloturée. Le montant total a déjà été calculé et payé pour cette réservation. Veuillez refaire une nouvelle réservation.");
            } else {
                // Verrou sur la ligne du plat qu'on veut commander (Row Share). Si quelqu'un d'autre a déjà verrouillé cette ligne, on attendra jusqu'à ce que son verrou soit relâché (Oracle le fait automatiquement).
                PreparedStatement pstmtLock = conn.prepareStatement("SELECT * FROM plat WHERE numplat = ? FOR UPDATE");
                pstmtLock.setInt(1, numplat);
                rs = pstmtLock.executeQuery();

                if (rs.next()) {
                    int qteServie = rs.getInt("qteservie");

                    // On vérifie qu'il y a assez de quantité pour notre commande
                    if (qteServie >= quantite) {

                        /*
                            On vérifie si une commande existe déjà avec ce plat-là. Si oui, on va additionner la quantité à la quantité déjà commandée
                            Exemple :
                                Client 1 : Je veux 1 assiette de crudités (le serveur valide 1 assiette de crudité).
                                Plus tard pendant la réservation, il reveut une autre assiette de crudités (pas de nouvelle commande, on ajoute juste 1 à la quantité commandée du plat)
                         */
                        PreparedStatement pstmtCommandCheck = conn.prepareStatement("SELECT * FROM commande WHERE numres = ? AND numplat = ?");
                        pstmtCommandCheck.setInt(1, numres);
                        pstmtCommandCheck.setInt(2, numplat);
                        ResultSet rsCommand = pstmtCommandCheck.executeQuery();

                        if(rsCommand.next()) {
                            // Si une commande existe déjà, on va ajouter la quantité à la quantité déjà commandée
                            PreparedStatement pstmtUpdateCommand = conn.prepareStatement("UPDATE commande SET quantite = quantite + ? WHERE numres = ? AND numplat = ?");
                            pstmtUpdateCommand.setInt(1, quantite);
                            pstmtUpdateCommand.setInt(2, numres);
                            pstmtUpdateCommand.setInt(3, numplat);
                            pstmtUpdateCommand.executeUpdate();
                        } else {
                            // On ajoute notre nouvelle commande dans la table commande
                            PreparedStatement pstmtOrder = conn.prepareStatement("INSERT INTO commande (numres, numplat, quantite) VALUES (?, ?, ?)");
                            pstmtOrder.setInt(1, numres);
                            pstmtOrder.setInt(2, numplat);
                            pstmtOrder.setInt(3, quantite);
                            pstmtOrder.executeUpdate();
                        }

                        // On met à jour la quantité servie dans la table plat
                        PreparedStatement pstmtUpdate = conn.prepareStatement("UPDATE plat SET qteservie = qteservie - ? WHERE numplat = ?");
                        pstmtUpdate.setInt(1, quantite);
                        pstmtUpdate.setInt(2, numplat);
                        pstmtUpdate.executeUpdate();

                        // On ne valide que si l'insertion ET la mise à jour ont été faites avec succès (si on ne le faisait pas, on aurait des incohérences dans la BD)
                        conn.commit();

                        System.out.println("Commande effectuée avec succès !");
                    } else {
                        System.err.println("Quantité insuffisante. Commande n'a pas été effectuée.");
                    }
                }
                System.err.println("Numéro de réservation, ou numéro de plat invalide. Commande n'a pas été effectuée.");
            }

        } catch(SQLException e) {
            // S'il y a une erreur, on annule la commande et on relâche le verrou sur la ligne du plat (rollback lache le verrou implicitement)
            try {
                System.err.println("Annulation de la transaction (rollback)");
                conn.rollback();
            } catch (SQLException e1) {
                e1.printStackTrace();
            }
            System.err.println("Erreur lors de la communication avec la base de données");
        }
    }

    /**
     * @return le menu de fonctionnalités du serveur
     */
    public String getMenu(){
        String res = "--------------------------\n";
        res += "Menu serveur : \n";
        res += "0. Quitter\n";
        res += "1. Consulter les tables disponibles pour une date et heure données.\n";
        res += "2. Réserver une table pour une date et heure données.\n";
        res += "3. Consulter les plats disponibles pour une éventuelle commande.\n";
        res += "4. Commander des plats.\n";
        return res;
    }

    /**
     * @return le numéro du serveur
     */
    public String getNom() {
        return nom;
    }
}
