import java.util.Random;

public class Test {

	public static void main(String[] args) {
		
		Random rd = new Random();
	
		
		for (int i = 0; i < 1000; i++) {
			

			int randomY = rd.ints(0,3)
					.findFirst()
					.getAsInt();
			
			System.out.println(randomY + "|");
			
		}
		

	}

}
