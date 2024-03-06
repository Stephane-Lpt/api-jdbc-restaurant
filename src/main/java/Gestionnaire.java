import java.sql.*;

/**
 * Classe Gestionnaire qui s'occupe d'autres opérations en plus des opérations d'un simple serveur (hérite de Serveur)
 */
public class Gestionnaire extends Serveur{
    /**
     * Constructeur de la classe Gestionnaire
     * @param numserv Le numéro du gestionnaire
     * @param email L'email du gestionnaire
     * @param nom Le nom du gestionnaire
     */
    public Gestionnaire(String numserv, String email, String nom) {
        super(numserv, email, nom);
    }

    /**
     * Consulter les affectations des serveurs
     * @param conn La connexion à la base de données
     */
    public void consulterAffectations(Connection conn) {
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM affecter");

            while(rs.next()) {
                System.out.println("Numéro de la table : " + rs.getInt("numtab") + ", Date d'affectation : " + rs.getDate("dataff") + ", Numéro du serveur : " + rs.getInt("numserv"));
            }
        } catch(SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Affecter un serveur à une table
     * @param conn La connexion à la base de données
     * @param numtab Le numéro de la table
     * @param numserv Le numéro du serveur
     */
    public void affecterServeurTable(Connection conn, int numtab, int numserv) {
        try {
            conn.setAutoCommit(false);

            Date dataff = new Date(System.currentTimeMillis()); // Date et heure au moment de l'affectation

            // Malheureusement, on ne peut pas empêcher l'overwrite. C'est-à-dire que l'on ne peut pas empêcher qu'un autre gestionnaire écrase notre mise à jour d'affectation d'un serveur par la sienne.
            // Cela, même avec un Row Share car il intervient uniquement sur la lecture et non pas l'écriture. Le Row Share ne serait qu'un sursis.
            PreparedStatement pstmtCheck = conn.prepareStatement("SELECT * FROM affecter WHERE numtab = ?");
            pstmtCheck.setInt(1, numtab);
            ResultSet rs = pstmtCheck.executeQuery();

            if(rs.next()) {
                // S'il y a déjà une affectation d'un serveur, on la met à jour.
                System.out.println("Il y a déjà une affectation pour cette table. Mise à jour de l'affectation en cours...");
                PreparedStatement pstmtUpdate = conn.prepareStatement("UPDATE affecter SET numserv = ? WHERE numtab = ? AND dataff = ?");
                pstmtUpdate.setInt(1, numserv);
                pstmtUpdate.setInt(2, numtab);
                pstmtUpdate.setDate(3, dataff);
                pstmtUpdate.executeUpdate();
            } else {
                // S'il n'y a pas déjà une affectation, alors on en créée une nouvelle
                PreparedStatement pstmtInsert = conn.prepareStatement("INSERT INTO affecter (numtab, dataff, numserv) VALUES (?, ?, ?)");
                pstmtInsert.setInt(1, numtab);
                pstmtInsert.setDate(2, dataff);
                pstmtInsert.setInt(3, numserv);
                pstmtInsert.executeUpdate();
            }

            /*
                Seulement à la fin des opérations et s'il n'y a pas eu d'erreur comme par exemple un autre gestionnaire qui a fait la même affectation (insertion plus particulièrement)
                 de serveur à la même table que nous. Alors on commit. Sinon, on annule l'opération du dernier gestionnaire arrivé.
                 Le verrouilage est optimiste car on part du principe qu'il y a encore moins de gestionnaires que de serveurs dans un restaurant (<5)
             */
            conn.commit();

            System.out.println("Serveur numéro " + numserv + " assigné à la table numéro " + numtab + " avec succès.");

        } catch(SQLException e) {
            // S'il y a une erreur, on annule tout.
            try {
                System.err.println("Annulation de la transaction (rollback)");
                conn.rollback();
            } catch (SQLException e1) {
                e1.printStackTrace();
            }
            System.err.println("Le numéro de table ou de serveur n'est pas valide."); // !!! Je n'élabore pas sur la gestion des erreurs SQL car le but de ce TP est de travailler les transactions.
        }
    }

    /**
     * Calculer le montant total d’une réservation consommée (numéro de réservation) et mettre à jour la table RESERVATION pour l’encaissement.
     * @param conn La connexion à la base de données
     * @param numres Le numéro de la réservation
     */
    public void calculerMontantTotalCommandeEtMajReservation(Connection conn, int numres) {
        try {

            conn.setAutoCommit(false);

            /*
                IMPORTANT !!!!
                Verrouillage de la table commande entière pour éviter tout ajout (on ne peut pas filtrer selon le numres) (Verrou Exclusif)
             */
            Statement stmtLock = conn.createStatement();
            stmtLock.execute("LOCK TABLE commande IN EXCLUSIVE MODE");

            /*
             Verrouillage de la ligne de la réservation (pour éviter qu'à l'avenir, si quelqu'un met à jour le moyen de paiement,
                cela écrase notre mise à jour du montant total de la commande
             */
            PreparedStatement pstmtLock = conn.prepareStatement("SELECT * FROM reservation WHERE numres = ? FOR UPDATE");
            pstmtLock.setInt(1, numres);
            ResultSet rs = pstmtLock.executeQuery();

            if(rs.next()) {
                /*
                    IMPORTANT !!!!
                    Si quelqu'un a déjà calculé le montant total de la commande, alors on ne va pas le recalculer.
                    Deux raisons :
                        Sécurité : On ne veut pas que quelqu'un puisse trafiquer le montant total de la commande.
                        Performance : On ne veut pas recalculer le montant total de la commande si c'est déjà fait d'autant plus
                            que c'est une opération coûteuse qui va empêcher tous les autres serveurs de faire une commande (à cause du verrrouillage exclusif ci-dessus)
                 */
                double montcom = rs.getDouble("montcom");
                if(montcom == 0) {
                    // On calcule le montant total de la commande
                    PreparedStatement pstmtTotal = conn.prepareStatement(
                            "SELECT SUM(p.prixunit * c.quantite) AS total " +
                                    "FROM commande c JOIN plat p ON c.numplat = p.numplat " +
                                    "WHERE c.numres = ?");
                    pstmtTotal.setInt(1, numres);
                    ResultSet rsTotal = pstmtTotal.executeQuery();

                    if (rsTotal.next()) {
                        double total = rsTotal.getDouble("total");

                    /*
                        On met à jour le montant total de la réservation après l'avoir calculé (ci-dessus)
                     */
                        PreparedStatement pstmtUpdate = conn.prepareStatement(
                                "UPDATE reservation SET montcom = ? WHERE numres = ?");
                        pstmtUpdate.setDouble(1, total);
                        pstmtUpdate.setInt(2, numres);
                        pstmtUpdate.executeUpdate();

                        System.out.println("Montant total calculé et mise à jour de l'état de la réservation avec succès.");
                    } else {
                        System.out.println("Pas de réservation trouvé pour ce numéro de réservation.");
                    }
                } else {
                    System.err.println("Le montant total de la réservation a déjà été calculé.");
                }
            } else {
                System.err.println("Pas de réservation trouvé pour ce numéro de réservation.");
            }

            // On valide l'opération
            conn.commit();

        } catch(SQLException e) {
            // S'il y a une erreur, on annule toutes les opérations en faisant un rollback (et on lâche les verrous, rollback le fait implicitement)
            try {
                System.err.println("Annulation de la transaction (rollback)");
                conn.rollback();
            } catch (SQLException e1) {
                e1.printStackTrace();
            }
            e.printStackTrace();
        }
    }

    /**
     * @return Le menu du gestionnaire qui contient les opérations supplémentaires par rapport à un simple serveur
     */
    @Override
    public String getMenu(){
        String res = super.getMenu();
        res += "5. Consulter les affectations des serveurs.\n";
        res += "6. Affecter des serveurs à des tables.\n";
        res += "7. Calculer le montant total d’une réservation consommée (numéro de réservation) et mettre à jour la table RESERVATION pour l’encaissement.\n";
        return res;
    }
}
