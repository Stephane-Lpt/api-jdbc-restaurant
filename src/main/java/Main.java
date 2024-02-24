import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws SQLException {
        Scanner sc = new Scanner(System.in);

        System.out.println("Veuillez entrer votre login :");
        String login = sc.nextLine();
        login = login.replaceAll("[\r\n]+", "");

        System.out.println("Veuillez entrer votre mot de passe :");
        String password = sc.nextLine();
        password = password.replaceAll("[\r\n]+", "");


        Connection con1 = DriverManager.getConnection("jdbc:oracle:thin:@charlemagne.iutnc.univ-lorraine.fr:1521:infodb", login, password);
        con1.setAutoCommit(false);







        con1.close();
    }
}
