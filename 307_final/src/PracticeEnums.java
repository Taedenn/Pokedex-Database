import java.util.ArrayList;

public class PracticeEnums {
	
	public static void main(String[] args) {
		String stat = "total";
		PokeStats statName = PokeStats.valueOf(stat.toUpperCase());
		
		//int statNum = 7;
		//PokeStats statName = PokeStats.values()[statNum-1];
		ArrayList<Integer> listPokemon = new ArrayList<Integer>();
		listPokemon.add(0, 1);
		listPokemon.add(0, 2);
		System.out.println(listPokemon.get(0));
		System.out.println("Selected: " + statName + " " + (statName.ordinal() + 1));
		
	}
}
