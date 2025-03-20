import java.awt.Color;
import java.awt.Point;

public class TableJeux {
    private Pion[][] table;
    public final int TAILLE = 3;
    private Joueur joueurGagnant;
    private Joueur J1;
    private Joueur J2;

    public TableJeux() {
        table = new Pion[TAILLE][TAILLE];
        initTableDeJeux();
    }

    public void setJ1(Joueur j1) {
        J1 = j1;
        J1.setCouleur(Color.BLUE);
    }

    public void setJ2(Joueur j2) {
        J2 = j2;
        J2.setCouleur(Color.GREEN);
    }

    private void initTableDeJeux() {
        for (int i = 0; i < TAILLE; i++) {
            for (int j = 0; j < TAILLE; j++) {
                table[i][j] = null;
            }
        }
    }

    public Joueur getJoueurGagnant() {
        return joueurGagnant;
    }

    public int jouer(Pion pion) {
        Point coord = pion.getCoordonnes();
        if (coord.x < 0 || coord.x >= TAILLE || coord.y < 0 || coord.y >= TAILLE || table[coord.x][coord.y] != null) {
            return 2; 
        }
        table[coord.x][coord.y] = pion;

        if (estGagnant(pion)) {
            joueurGagnant = pion.getCouleur().equals(J1.getCouleur()) ? J1 : J2;
            return 1;
        }
        return 0;
    }

    private boolean estGagnant(Pion pion) {
        Color couleur = pion.getCouleur();

        for (int i = 0; i < TAILLE; i++) {
            if ((table[i][0] != null && table[i][1] != null && table[i][2] != null &&
                    table[i][0].getCouleur().equals(couleur) &&
                    table[i][1].getCouleur().equals(couleur) &&
                    table[i][2].getCouleur().equals(couleur)) ||
                (table[0][i] != null && table[1][i] != null && table[2][i] != null &&
                    table[0][i].getCouleur().equals(couleur) &&
                    table[1][i].getCouleur().equals(couleur) &&
                    table[2][i].getCouleur().equals(couleur))) {
                return true;
            }
        }

        if ((table[0][0] != null && table[1][1] != null && table[2][2] != null &&
             table[0][0].getCouleur().equals(couleur) &&
             table[1][1].getCouleur().equals(couleur) &&
             table[2][2].getCouleur().equals(couleur)) ||
            (table[0][2] != null && table[1][1] != null && table[2][0] != null &&
             table[0][2].getCouleur().equals(couleur) &&
             table[1][1].getCouleur().equals(couleur) &&
             table[2][0].getCouleur().equals(couleur))) {
            return true;
        }

        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < TAILLE; i++) {
            for (int j = 0; j < TAILLE; j++) {
                if (table[i][j] == null) sb.append("[ ]");
                else if (table[i][j].getCouleur().equals(J1.getCouleur())) sb.append("[X]");
                else sb.append("[O]");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
