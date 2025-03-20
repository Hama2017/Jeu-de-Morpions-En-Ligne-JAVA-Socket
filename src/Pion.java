import java.awt.Color;
import java.awt.Point;

public class Pion {
    private Color couleur;
    private Point coordonnes;

    public Pion(Color couleur, Point coordonnes) {
        this.couleur = couleur;
        this.coordonnes = coordonnes;
    }

    public Color getCouleur() {
        return couleur;
    }

    public Point getCoordonnes() {
        return coordonnes;
    }
}
