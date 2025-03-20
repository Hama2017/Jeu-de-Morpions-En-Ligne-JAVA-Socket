import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServeurIHM {
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
    
    private JFrame frame;
    private JPanel gamePanel;
    private JButton[][] buttons;
    private JLabel statusLabel;
    private JTextArea logArea;
    private JScrollPane logScrollPane;
    
    public ServeurIHM() {
        tableJeux = new TableJeux();
        executor = Executors.newFixedThreadPool(2);
        initIHM();
    }
    
    private void initIHM() {
        frame = new JFrame("Serveur Morpion - Supervision");
        frame.setSize(600, 500);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(10, 10));
        
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                fermerConnexions();
                executor.shutdown();
                System.exit(0);
            }
        });
        
        JPanel statusPanel = new JPanel();
        statusLabel = new JLabel("Serveur démarré. En attente de connexions...");
        statusPanel.add(statusLabel);
        frame.add(statusPanel, BorderLayout.NORTH);
        
        gamePanel = new JPanel();
        gamePanel.setLayout(new GridLayout(3, 3, 5, 5));
        buttons = new JButton[3][3];
        
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                buttons[i][j] = new JButton();
                buttons[i][j].setFont(new Font("Arial", Font.BOLD, 40));
                buttons[i][j].setEnabled(false); 
                gamePanel.add(buttons[i][j]);
            }
        }
        
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(gamePanel, BorderLayout.CENTER);
        frame.add(centerPanel, BorderLayout.CENTER);
        
        logArea = new JTextArea(10, 40);
        logArea.setEditable(false);
        logScrollPane = new JScrollPane(logArea);
        logScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        
        frame.add(logScrollPane, BorderLayout.SOUTH);
        
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
    
    public void demarrer() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                logMessage("Serveur démarré sur le port " + PORT);
                logMessage("En attente de deux joueurs...");
                statusLabel.setText("En attente de deux joueurs...");
                
                joueur1Socket = serverSocket.accept();
                logMessage("Premier joueur connecté depuis " + joueur1Socket.getInetAddress());
                outJoueur1 = new PrintWriter(joueur1Socket.getOutputStream(), true);
                inJoueur1 = new BufferedReader(new InputStreamReader(joueur1Socket.getInputStream()));
                
                pseudoJoueur1 = inJoueur1.readLine();
                j1 = new Joueur(pseudoJoueur1);
                tableJeux.setJ1(j1);
                logMessage("Joueur 1: " + pseudoJoueur1);
                
                outJoueur1.println("Bienvenue " + pseudoJoueur1 + "! En attente du deuxième joueur...");
                statusLabel.setText("Joueur 1 connecté: " + pseudoJoueur1 + ". En attente du joueur 2...");
                
                joueur2Socket = serverSocket.accept();
                logMessage("Deuxième joueur connecté depuis " + joueur2Socket.getInetAddress());
                outJoueur2 = new PrintWriter(joueur2Socket.getOutputStream(), true);
                inJoueur2 = new BufferedReader(new InputStreamReader(joueur2Socket.getInputStream()));
                
                pseudoJoueur2 = inJoueur2.readLine();
                j2 = new Joueur(pseudoJoueur2);
                tableJeux.setJ2(j2);
                logMessage("Joueur 2: " + pseudoJoueur2);
                
                statusLabel.setText("Partie en cours: " + pseudoJoueur1 + " vs " + pseudoJoueur2);
                outJoueur1.println("INIT:" + pseudoJoueur1 + ":" + pseudoJoueur2 + ":X:O");
                outJoueur2.println("INIT:" + pseudoJoueur2 + ":" + pseudoJoueur1 + ":O:X");
                
                jouerPartie();
                
            } catch (IOException e) {
                logMessage("Erreur serveur: " + e.getMessage());
                e.printStackTrace();
            } finally {
                fermerConnexions();
            }
        }).start();
    }
    
    private void jouerPartie() throws IOException {
        boolean tourJoueur1 = true;
        int etatJeu = 0;
        
        outJoueur1.println(JOUER);
        outJoueur2.println(ATTENDRE);
        logMessage("La partie commence. C'est au tour de " + pseudoJoueur1);
        
        while (etatJeu != 1) {
            if (tourJoueur1) {
                statusLabel.setText("Tour de " + pseudoJoueur1 + " (X)");
                etatJeu = traiterTourJoueur(inJoueur1, outJoueur1, j1, pseudoJoueur1);
                if (etatJeu == -1) {
                    logMessage(pseudoJoueur1 + " s'est déconnecté, fin de la partie");
                    outJoueur2.println("ADVERSAIRE_DECONNECTE");
                    break;
                } else if (etatJeu == 0) {
                    envoyerMiseAJour();
                    tourJoueur1 = false;
                    outJoueur1.println(ATTENDRE);
                    outJoueur2.println(JOUER);
                    logMessage("C'est au tour de " + pseudoJoueur2);
                }
            } else {
                statusLabel.setText("Tour de " + pseudoJoueur2 + " (O)");
                etatJeu = traiterTourJoueur(inJoueur2, outJoueur2, j2, pseudoJoueur2);
                if (etatJeu == -1) {
                    logMessage(pseudoJoueur2 + " s'est déconnecté, fin de la partie");
                    outJoueur1.println("ADVERSAIRE_DECONNECTE");
                    break;
                } else if (etatJeu == 0) {
                    envoyerMiseAJour();
                    tourJoueur1 = true;
                    outJoueur2.println(ATTENDRE);
                    outJoueur1.println(JOUER);
                    logMessage("C'est au tour de " + pseudoJoueur1);
                }
            }
            
            if (tableJeux.estTableauPlein() && etatJeu != 1) {
                logMessage("Match nul !");
                statusLabel.setText("Match nul !");
                outJoueur1.println("MATCH_NUL");
                outJoueur2.println("MATCH_NUL");
                break;
            }
        }
        
        if (etatJeu == 1) {
        	
        	mettreAJourGrille(tableJeux.toStringForClient());
            Joueur gagnant = tableJeux.getJoueurGagnant();
            String messageGagnant = "GAGNANT:" + gagnant.getPseudo();
            logMessage("Partie terminée. Le gagnant est " + gagnant.getPseudo());
            statusLabel.setText("Le gagnant est " + gagnant.getPseudo() + " !");
            outJoueur1.println(messageGagnant);
            outJoueur2.println(messageGagnant);
        }
    }
    
    private int traiterTourJoueur(BufferedReader in, PrintWriter out, Joueur joueur, String nomJoueur) throws IOException {
        String coup;
        int etatJeu;
        
        do {
            coup = in.readLine();
            if (coup == null) {
                logMessage(nomJoueur + " s'est déconnecté");
                return -1; 
            }
            
            logMessage(nomJoueur + " joue: " + coup);
            
            try {
                String[] coords = coup.split(",");
                int x = Integer.parseInt(coords[0]);
                int y = Integer.parseInt(coords[1]);
                
                etatJeu = tableJeux.jouer(new Pion(joueur.getCouleur(), new java.awt.Point(x, y)));
                
                if (etatJeu == 2) {
                    logMessage("Coup invalide, demande de rejouer");
                    out.println(REJOUER);
                }
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                logMessage("Format de coup invalide: " + coup);
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
        
        SwingUtilities.invokeLater(() -> {
            mettreAJourGrille(etatTable);
        });
    }
    
    private void mettreAJourGrille(String etatTable) {
        String[] cells = etatTable.split(",");
        int index = 0;
        
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                String cell = cells[index++];
                
                if (cell.equals("X")) {
                    buttons[i][j].setText("X");
                    buttons[i][j].setForeground(Color.BLUE);
                } else if (cell.equals("O")) {
                    buttons[i][j].setText("O");
                    buttons[i][j].setForeground(Color.GREEN);
                } else {
                    buttons[i][j].setText("");
                }
            }
        }
    }
    
    private void logMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
        System.out.println(message);
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
        SwingUtilities.invokeLater(() -> {
            ServeurIHM serveur = new ServeurIHM();
            serveur.demarrer();
        });
    }
}