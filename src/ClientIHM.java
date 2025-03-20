import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.Socket;

public class ClientIHM {
    private JFrame frame;
    private JPanel gamePanel;
    private JButton[][] buttons;
    private JLabel statusLabel;
    private JLabel infoLabel;
    
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    
    private String pseudo;
    private String adversaire;
    private Color maCouleur;
    private Color couleurAdversaire;
    private boolean monTour = false;
    
    private static final int TAILLE = 3;
    private static final String JOUER = "JOUER";
    private static final String ATTENDRE = "ATTENDRE";
    private static final String REJOUER = "REJOUER";
    private static final String MISE_A_JOUR = "MISE_A_JOUR";
    
    public ClientIHM() {
        // Demander le pseudo
        pseudo = JOptionPane.showInputDialog(null, "Entrez votre pseudo:", "Connexion", JOptionPane.QUESTION_MESSAGE);
        if (pseudo == null || pseudo.trim().isEmpty()) {
            pseudo = "Joueur" + (int)(Math.random() * 1000);
        }
        
        // Initialiser l'interface graphique
        initIHM();
        
        // Connecter au serveur
        connecterAuServeur();
    }
    
    private void initIHM() {
        frame = new JFrame("Morpion en réseau - " + pseudo);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 500);
        frame.setLayout(new BorderLayout());
        
        // Panel d'information en haut
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new GridLayout(2, 1));
        
        infoLabel = new JLabel("En attente de connexion...", JLabel.CENTER);
        infoLabel.setFont(new Font("Arial", Font.BOLD, 14));
        
        statusLabel = new JLabel("Veuillez patienter", JLabel.CENTER);
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        
        infoPanel.add(infoLabel);
        infoPanel.add(statusLabel);
        frame.add(infoPanel, BorderLayout.NORTH);
        
        // Grille de jeu
        gamePanel = new JPanel();
        gamePanel.setLayout(new GridLayout(TAILLE, TAILLE));
        buttons = new JButton[TAILLE][TAILLE];
        
        for (int i = 0; i < TAILLE; i++) {
            for (int j = 0; j < TAILLE; j++) {
                buttons[i][j] = new JButton();
                buttons[i][j].setFont(new Font("Arial", Font.BOLD, 40));
                buttons[i][j].setFocusPainted(false);
                
                final int x = i;
                final int y = j;
                buttons[i][j].addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        if (monTour) {
                            envoyerCoup(x, y);
                            // Désactiver le bouton immédiatement après le clic
                            ((JButton)e.getSource()).setEnabled(false);
                            statusLabel.setText("En attente de la réponse du serveur...");
                        } else {
                            statusLabel.setText("Ce n'est pas votre tour");
                        }
                    }
                });
                
                gamePanel.add(buttons[i][j]);
            }
        }
        
        frame.add(gamePanel, BorderLayout.CENTER);
        
        // Rendre visible
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
    
    private void connecterAuServeur() {
        try {
            socket = new Socket("localhost", 5001);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            
            // Envoyer le pseudo
            out.println(pseudo);
            
            // Thread pour écouter les messages du serveur
            new Thread(this::ecouterServeur).start();
            
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Impossible de se connecter au serveur: " + e.getMessage(), 
                                         "Erreur de connexion", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
    
    private void ecouterServeur() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                traiterMessage(message);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Connexion au serveur perdue: " + e.getMessage(), 
                                         "Erreur de connexion", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
    
    private void traiterMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            if (message.startsWith("INIT:")) {
                // Format: INIT:monPseudo:adversairePseudo:maCouleur:couleurAdversaire
                String[] parts = message.split(":");
                pseudo = parts[1];
                adversaire = parts[2];
                maCouleur = stringToColor(parts[3]);
                couleurAdversaire = stringToColor(parts[4]);
                
                frame.setTitle("Morpion en réseau - " + pseudo + " vs " + adversaire);
                infoLabel.setText("Vous jouez contre " + adversaire);
                
            } else if (message.equals(JOUER)) {
                monTour = true;
                statusLabel.setText("C'est à votre tour de jouer");
                // Réactiver les boutons vides quand c'est notre tour
                reactiveButtons();
                
            } else if (message.equals(ATTENDRE)) {
                monTour = false;
                statusLabel.setText("En attente du coup de " + adversaire);
                // Désactiver tous les boutons quand ce n'est pas notre tour
                disableAllButtons();
                
            } else if (message.equals(REJOUER)) {
                statusLabel.setText("Case occupée ou invalide, rejouez");
                monTour = true; // On reste à notre tour
                reactiveButtons(); // Réactiver les boutons vides
                
            } else if (message.startsWith(MISE_A_JOUR)) {
                // Format: MISE_A_JOUR:E,E,X,E,O,E,E,E,E
                String etatTable = message.substring(message.indexOf(":") + 1);
                mettreAJourGrille(etatTable);
                
                // Après une mise à jour, activer/désactiver les boutons selon si c'est notre tour
                if (monTour) {
                    reactiveButtons();
                } else {
                    disableAllButtons();
                }
                
            } else if (message.startsWith("GAGNANT:")) {
                String gagnant = message.substring(message.indexOf(":") + 1);
                if (gagnant.equals(pseudo)) {
                    JOptionPane.showMessageDialog(frame, "Félicitations ! Vous avez gagné !", 
                                                "Fin de partie", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(frame, gagnant + " a gagné la partie.", 
                                                "Fin de partie", JOptionPane.INFORMATION_MESSAGE);
                }
                desactiverBoutons();
                
            } else if (message.equals("MATCH_NUL")) {
                JOptionPane.showMessageDialog(frame, "Match nul !", 
                                            "Fin de partie", JOptionPane.INFORMATION_MESSAGE);
                desactiverBoutons();
                
            } else if (message.startsWith("Bienvenue")) {
                statusLabel.setText(message);
            }
        });
    }
    
    private void envoyerCoup(int x, int y) {
        System.out.println("Envoi du coup: " + x + "," + y);
        out.println(x + "," + y);
        // Ne pas modifier monTour ici - attendre la confirmation du serveur
    }
    
    private void mettreAJourGrille(String etatTable) {
        String[] cells = etatTable.split(",");
        int index = 0;
        
        for (int i = 0; i < TAILLE; i++) {
            for (int j = 0; j < TAILLE; j++) {
                String cell = cells[index++];
                
                if (cell.equals("X")) {
                    buttons[i][j].setText("X");
                    buttons[i][j].setForeground(Color.BLUE);
                    buttons[i][j].setEnabled(false);
                } else if (cell.equals("O")) {
                    buttons[i][j].setText("O");
                    buttons[i][j].setForeground(Color.GREEN);
                    buttons[i][j].setEnabled(false);
                } else {
                    buttons[i][j].setText("");
                    // Réactiver uniquement les cases vides si c'est le tour du joueur
                    // Dans le cas d'une case vide
                    if (monTour) {
                        buttons[i][j].setEnabled(true);
                    } else {
                        buttons[i][j].setEnabled(false);
                    }
                }
            }
        }
        
        // Mettre à jour le statut après l'actualisation de la grille
        if (monTour) {
            statusLabel.setText("C'est à votre tour de jouer");
        } else {
            statusLabel.setText("En attente du coup de " + adversaire);
        }
    }
    
    private void desactiverBoutons() {
        for (int i = 0; i < TAILLE; i++) {
            for (int j = 0; j < TAILLE; j++) {
                buttons[i][j].setEnabled(false);
            }
        }
    }
    
    private void disableAllButtons() {
        for (int i = 0; i < TAILLE; i++) {
            for (int j = 0; j < TAILLE; j++) {
                buttons[i][j].setEnabled(false);
            }
        }
    }
    
    private void reactiveButtons() {
        for (int i = 0; i < TAILLE; i++) {
            for (int j = 0; j < TAILLE; j++) {
                // Réactiver uniquement les boutons vides
                if (buttons[i][j].getText().isEmpty()) {
                    buttons[i][j].setEnabled(true);
                }
            }
        }
    }
    
    private Color stringToColor(String colorName) {
        switch (colorName) {
            case "BLUE": return Color.BLUE;
            case "GREEN": return Color.GREEN;
            case "RED": return Color.RED;
            default: return Color.BLACK;
        }
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new ClientIHM();
        });
    }
}