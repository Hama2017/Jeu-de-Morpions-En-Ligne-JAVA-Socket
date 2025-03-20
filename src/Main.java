import java.awt.Color;
import java.awt.Point;

public class Main {
	
	public static void main(String... arg) {
		
		TableJeux tj = new TableJeux();
		
		Joueur j1 = new Joueur("Hama");
		Joueur j2 = new Joueur("Amina");
		
		tj.setJ1(j1);
		tj.setJ2(j2);

		
		tj.jouer(new Pion(j1.getCouleur(), new Point(1,1)));
		tj.jouer(new Pion(j1.getCouleur(), new Point(1,0)));
		
		tj.jouer(new Pion(j2.getCouleur(), new Point(0,0)));
		tj.jouer(new Pion(j2.getCouleur(), new Point(0,0)));
		
	System.out.println(tj);

		
		
		
	}
}
