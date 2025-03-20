import java.awt.Point;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;

public class Serveur {

    public static final String COMMENCER = "COMMENCER";
    public static final String TERMINER = "TERMINER";
    public static final String REJOUER = "REJOUER";
    public static final String CONTINUER = "CONTINUER";

    public static void main(String[] args) {
        TableJeux table = new TableJeux();
        Joueur serveur = new Joueur("Serveur");

        try (ServerSocket serverSocket = new ServerSocket(5001)) {
            System.out.println("Serveur en attente de connexion...");
            Socket clientSocket = serverSocket.accept();

            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

            String pseudoClient = in.readLine();
            System.out.println("Joueur connecté : " + pseudoClient);

            Joueur client = new Joueur(pseudoClient);
            table.setJ1(serveur);
            table.setJ2(client);

            String demande = in.readLine();
            if (demande.equals(COMMENCER)) {
                out.println("La partie commence, le serveur joue en premier !\n" + table);

                int etatJeu;
                while (true) {
                    do {
                        Pion pionServeur = genererPionServeur(serveur);
                        etatJeu = table.jouer(pionServeur);
                    } while (etatJeu == 2);

                    if (etatJeu == 1) {
                        out.println("Partie TERMINÉE - Le Gagnant est " + table.getJoueurGagnant().getPseudo());
                        break;
                    }

                    out.println(table);
                    out.println("FIN");

                    String coupClient;
                    do {
                        coupClient = in.readLine();
                        String[] coords = coupClient.split(",");
                        Point point = new Point(Integer.parseInt(coords[0]), Integer.parseInt(coords[1]));
                        Pion pionClient = new Pion(client.getCouleur(), point);
                        etatJeu = table.jouer(pionClient);

                        if (etatJeu == 2) {
                            out.println(REJOUER);
                        } else if (etatJeu == 1) {
                            out.println(TERMINER);
                            return;
                        } else {
                            out.println(CONTINUER);
                        }
                    } while (etatJeu == 2);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Pion genererPionServeur(Joueur joueur) {
        Random random = new Random();
        int x = random.nextInt(3);
        int y = random.nextInt(3);
        return new Pion(joueur.getCouleur(), new Point(x, y));
    }
}
