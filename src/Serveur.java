import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Serveur {
    private static final int PORT = 5001;
    private static final String JOUER = "JOUER";
    private static final String ATTENDRE = "ATTENDRE";
    private static final String REJOUER = "REJOUER";
    private static final String TERMINER = "TERMINER";
    private static final String MISE_A_JOUR = "MISE_A_JOUR";
    
    private TableJeux tableJeux;
    private Socket joueur1Socket;
    private Socket joueur2Socket;
    private PrintWriter outJoueur1;
    private PrintWriter outJoueur2;
    private BufferedReader inJoueur1;
    private BufferedReader inJoueur2;
    private String pseudoJoueur1;
    private String pseudoJoueur2;
    private Joueur j1;
    private Joueur j2;
    private ExecutorService executor;
    
    public Serveur() {
        tableJeux = new TableJeux();
        executor = Executors.newFixedThreadPool(2);
    }
    
    public void demarrer() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Serveur démarré sur le port " + PORT);
            System.out.println("En attente de deux joueurs...");
            
            // Attente du premier joueur
            joueur1Socket = serverSocket.accept();
            System.out.println("Premier joueur connecté");
            outJoueur1 = new PrintWriter(joueur1Socket.getOutputStream(), true);
            inJoueur1 = new BufferedReader(new InputStreamReader(joueur1Socket.getInputStream()));
            
            // Lecture du pseudo du joueur 1
            pseudoJoueur1 = inJoueur1.readLine();
            j1 = new Joueur(pseudoJoueur1);
            tableJeux.setJ1(j1);
            System.out.println("Joueur 1: " + pseudoJoueur1);
            
            // Informer le joueur 1 qu'il doit attendre
            outJoueur1.println("Bienvenue " + pseudoJoueur1 + "! En attente du deuxième joueur...");
            
            // Attente du deuxième joueur
            joueur2Socket = serverSocket.accept();
            System.out.println("Deuxième joueur connecté");
            outJoueur2 = new PrintWriter(joueur2Socket.getOutputStream(), true);
            inJoueur2 = new BufferedReader(new InputStreamReader(joueur2Socket.getInputStream()));
            
            // Lecture du pseudo du joueur 2
            pseudoJoueur2 = inJoueur2.readLine();
            j2 = new Joueur(pseudoJoueur2);
            tableJeux.setJ2(j2);
            System.out.println("Joueur 2: " + pseudoJoueur2);
            
            // Informer les deux joueurs que la partie commence
            outJoueur1.println("INIT:" + pseudoJoueur1 + ":" + pseudoJoueur2 + ":BLUE:GREEN");
            outJoueur2.println("INIT:" + pseudoJoueur2 + ":" + pseudoJoueur1 + ":GREEN:BLUE");
            
            // Démarrer la partie
            jouerPartie();
            
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            fermerConnexions();
        }
    }
    
    private void jouerPartie() throws IOException {
        boolean tourJoueur1 = true;
        int etatJeu = 0;
        
        // Informer joueur 1 qu'il commence
        outJoueur1.println(JOUER);
        outJoueur2.println(ATTENDRE);
        
        while (etatJeu != 1) {
            if (tourJoueur1) {
                etatJeu = traiterTourJoueur(inJoueur1, outJoueur1, j1, "Joueur 1");
                if (etatJeu == -1) {
                    // Déconnexion du joueur 1
                    System.out.println("Joueur 1 déconnecté, fin de la partie");
                    outJoueur2.println("ADVERSAIRE_DECONNECTE");
                    break;
                } else if (etatJeu == 0) {
                    // Mettre à jour les deux clients
                    envoyerMiseAJour();
                    // Changer de tour
                    tourJoueur1 = false;
                    outJoueur1.println(ATTENDRE);
                    outJoueur2.println(JOUER);
                }
            } else {
                etatJeu = traiterTourJoueur(inJoueur2, outJoueur2, j2, "Joueur 2");
                if (etatJeu == -1) {
                    // Déconnexion du joueur 2
                    System.out.println("Joueur 2 déconnecté, fin de la partie");
                    outJoueur1.println("ADVERSAIRE_DECONNECTE");
                    break;
                } else if (etatJeu == 0) {
                    // Mettre à jour les deux clients
                    envoyerMiseAJour();
                    // Changer de tour
                    tourJoueur1 = true;
                    outJoueur2.println(ATTENDRE);
                    outJoueur1.println(JOUER);
                }
            }
            
            if (tableJeux.estTableauPlein() && etatJeu != 1) {
                // Match nul
                outJoueur1.println("MATCH_NUL");
                outJoueur2.println("MATCH_NUL");
                break;
            }
        }
        
        if (etatJeu == 1) {
            Joueur gagnant = tableJeux.getJoueurGagnant();
            String messageGagnant = "GAGNANT:" + gagnant.getPseudo();
            outJoueur1.println(messageGagnant);
            outJoueur2.println(messageGagnant);
        }
    }
    
    private int traiterTourJoueur(BufferedReader in, PrintWriter out, Joueur joueur, String nomJoueur) throws IOException {
        String coup;
        int etatJeu;
        
        do {
            // Attendre la réception d'un coup
            coup = in.readLine();
            if (coup == null) {
                System.out.println(nomJoueur + " s'est déconnecté");
                return -1; // Indication de déconnexion
            }
            
            System.out.println(nomJoueur + " joue: " + coup);
            
            try {
                String[] coords = coup.split(",");
                int x = Integer.parseInt(coords[0]);
                int y = Integer.parseInt(coords[1]);
                
                etatJeu = tableJeux.jouer(new Pion(joueur.getCouleur(), new java.awt.Point(x, y)));
                
                if (etatJeu == 2) {
                    System.out.println("Coup invalide, demande de rejouer");
                    out.println(REJOUER);
                }
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                System.out.println("Format de coup invalide: " + coup);
                out.println(REJOUER);
                etatJeu = 2;
            }
        } while (etatJeu == 2);
        
        return etatJeu;
    }
    
    private void envoyerMiseAJour() {
        String etatTable = tableJeux.toStringForClient();
        outJoueur1.println(MISE_A_JOUR + ":" + etatTable);
        outJoueur2.println(MISE_A_JOUR + ":" + etatTable);
    }
    
    private void fermerConnexions() {
        try {
            if (joueur1Socket != null && !joueur1Socket.isClosed()) joueur1Socket.close();
            if (joueur2Socket != null && !joueur2Socket.isClosed()) joueur2Socket.close();
            executor.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args) {
        Serveur serveur = new Serveur();
        serveur.demarrer();
    }
}