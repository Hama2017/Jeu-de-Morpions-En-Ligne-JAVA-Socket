import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    public static void main(String[] args) {
        try (Socket socket = new Socket("localhost", 5001)) {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            Scanner sc = new Scanner(System.in);

            System.out.print("Entrez votre pseudo : ");
            String pseudo = sc.nextLine();
            out.println(pseudo);
            out.println("COMMENCER");

            String response;
            while ((response = in.readLine()) != null) {
                System.out.println(response);
                if (response.contains("FIN")) {
                    System.out.println("A vous de jouer Entrez la position (x,y) : ");
                    String move = sc.nextLine();
                    out.println(move);
                } else if (response.equals("REJOUER")) {
                    System.out.println("Case occupée rejouez !");
                    String move = sc.nextLine();
                    out.println(move);
                } else if (response.equals("TERMINER")) {
                    System.out.println("Partie terminée ");
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
