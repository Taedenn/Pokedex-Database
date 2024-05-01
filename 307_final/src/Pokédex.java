import java.sql.*;
import java.util.*;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
/**
 * An application that mimics a pokédex, uses a pokémon database and reads from
 * a pokémon xlsx sheet!
 * 
 * @author Taeden, referencing Kathy's Database.java
 */
public class Pokédex implements AutoCloseable {
	// necessary keys to connect to DB
	private static final String DB_NAME = "tca0103_pokedex";
	private static final String DB_USER = "token_b1e4";
	private static final String DB_PASSWORD = "b3xQfYdVfMrteN7V";
	
	public static Scanner in;			// general scanner for reading user input
	public static Scanner parse;		// scanner for parsing
	public static Scanner inception;	// scanner for further parsing parsed lines

	// selects pokémon ID and name contained in the pokédex by specified generation
	private final String LIST_POKEMON = """
			SELECT Pokedex.id, Pokedex.name
			FROM Pokedex
				LEFT OUTER JOIN Pokemon ON Pokedex.id = Pokemon.id
			WHERE gen = ?
			ORDER BY Pokedex.id;
			""";
	// selects pokémon name using its ID
	private final String SEARCH_BY_ID = """
			SELECT Pokedex.name
			FROM Pokedex
			WHERE Pokedex.id = ?
			""";
	// selects pokémon name using its ID using admin function
	private final String SEARCH_BY_ID_ADMIN = """
			SELECT Pokemon.name
			FROM Pokemon
			WHERE Pokemon.id = ?
			""";
	// selects pokémon by their name
	private final String SEARCH_BY_NAME = """
			SELECT id, name
			FROM Pokedex
			WHERE name = ?
			""";
	// selects pokémon if they contain a letter
	private final String SEARCH_BY_LETTER = """
			SELECT id, name
			FROM Pokedex
			WHERE name LIKE ?
			""";
	// selects pokémon if they have the specified type
	private final String SEARCH_BY_TYPE = """
			SELECT Pokedex.id, Pokedex.name
			FROM Pokedex
				INNER JOIN HasType ON Pokedex.id = HasType.pokemonId
				INNER JOIN Type ON Type.id = HasType.typeId
			WHERE Type.name = ?
			""";
	// selects pokémon if they have the specified dual type
	private final String SEARCH_DUAL_TYPE = """
			SELECT Pokedex.id, Pokedex.name, Type.name AS "firstType", SecondType.secondType
			FROM Pokedex
				INNER JOIN HasType ON Pokedex.id = HasType.pokemonId
				INNER JOIN Type ON Type.id = HasType.typeId
			    INNER JOIN (
			       	SELECT Pokedex.id AS "id", Type.name AS "secondType" FROM Pokedex
						INNER JOIN HasType ON Pokedex.id = HasType.pokemonId
						INNER JOIN Type ON Type.id = HasType.typeId
					WHERE Type.name LIKE ?
				) AS SecondType ON SecondType.id = Pokedex.id
			WHERE Type.name LIKE ?
			""";
	// selects a pokémon's hit points based on its ID
	private final String GET_HEALTH = """
			SELECT hp
			FROM BaseStatSet
				INNER JOIN Pokemon ON BaseStatSet.id = Pokemon.id
			WHERE Pokemon.id = ?
			""";
	// selects necessary data for pokemon entry and logic for certain access
	private final String GET_ENTRY_ADMIN = """
			SELECT Pokemon.name, InPokedex.seen, InPokedex.caught, Type.name AS "type", BaseStatSet.*
			FROM Pokemon
				INNER JOIN HasType ON Pokemon.id = HasType.pokemonId
			    INNER JOIN Type ON HasType.typeId = Type.id
			    INNER JOIN InPokedex ON Pokemon.id = InPokedex.id
			    INNER JOIN BaseStatSet ON Pokemon.id = BaseStatSet.id
			WHERE Pokemon.id = ?
			""";
	// selects necessary data for pokemon entry and logic
	private final String GET_ENTRY = """
			SELECT Pokedex.name, InPokedex.seen, InPokedex.caught, Type.name AS "type", BaseStatSet.*
			FROM Pokedex
				INNER JOIN HasType ON Pokedex.id = HasType.pokemonId
			    INNER JOIN Type ON HasType.typeId = Type.id
			    INNER JOIN InPokedex ON Pokedex.id = InPokedex.id
			    INNER JOIN BaseStatSet ON Pokedex.id = BaseStatSet.id
			WHERE Pokedex.id = ?
			""";
	// selects Type id from name
	private final String GET_TYPE_ID = """
			SELECT id FROM Type
			WHERE name = ?
			""";
	// aids insert type function
	private final String INSERT_TYPE = """
			INSERT INTO HasType (pokemonId, typeId) VALUES
				(?,?)
			""";
	// aids insert stats function
	private final String INSERT_STATS = """
			INSERT INTO BaseStatSet (id, hp, atk, def, satk, sdef, spd, total) VALUES
				(?, ?, ?, ?, ?, ?, ?, ?);
			""";
	// deletes HasType data for reset
	private final String RESET_TYPES = """
			DELETE FROM HasType
			WHERE pokemonId < 494;
			""";
	// deletes BaseStatSet data for reset
	private final String RESET_STATS = """
			DELETE FROM BaseStatSet
			WHERE id < 494;
			""";
	// checks if pokémon in pokédex was caught previously by its ID
	private final String WAS_CAUGHT = """
			SELECT InPokedex.caught
			FROM InPokedex
			WHERE id = ?
			""";
	// updates "seen" to true if the user fails to catch a pokémon
	private final String SEEN_POKEMON = """
			UPDATE InPokedex, Pokedex, Pokemon
			SET seen = 1,
				Pokedex.name = Pokemon.name
			WHERE Pokedex.id = InPokedex.id AND Pokedex.id = Pokemon.id AND Pokedex.id = ?
			""";
	// updates the pokédex and table InPokedex when a new pokémon is caught
	private final String ADD_TO_DEX = """
			UPDATE Pokedex, Pokemon, InPokedex
			SET Pokedex.name = Pokemon.name,
				InPokedex.seen = 1,
				InPokedex.caught = 1,
				InPokedex.inPokedex = 1
			WHERE Pokedex.id = InPokedex.id AND Pokedex.id = Pokemon.id AND Pokedex.id = ?;
			""";
	// updates pokédex and table InPokédex when a pokémon is released
	private final String REMOVE = """
			UPDATE Pokedex, InPokedex
			SET Pokedex.name = null,
				InPokedex.seen = 0,
				InPokedex.caught = 0,
				InPokedex.inPokedex = 0
			WHERE Pokedex.id = InPokedex.id AND Pokedex.id = ?;
			""";

	// prepared statments that synch up with SQL Queries
	private PreparedStatement listPokemon;

	private PreparedStatement searchById;
	private PreparedStatement searchByIdAdmin;
	private PreparedStatement searchByName;
	private PreparedStatement searchByLetter;
	private PreparedStatement searchByType;
	private PreparedStatement searchByDualType;

	private PreparedStatement getHealth;
	private PreparedStatement getEntryAdmin;
	private PreparedStatement getEntry;
	private PreparedStatement getTypeId;

	private PreparedStatement insertType;
	private PreparedStatement insertStats;

	private PreparedStatement resetTypes;
	private PreparedStatement resetStats;

	private PreparedStatement wasCaught;
	private PreparedStatement seenPokemon;
	private PreparedStatement addToDex;
	private PreparedStatement remove;

	// declare variables for constructor
	private final String dbHost;
	private final int dbPort;
	private final String dbName;
	private final String dbUser, dbPassword;

	private Connection connection;

	// CONSTRUCTOR allows connection to database
	public Pokédex(String dbHost, int dbPort, String dbName, String dbUser, String dbPassword) throws SQLException {
		this.dbHost = dbHost;
		this.dbPort = dbPort;
		this.dbName = dbName;
		this.dbUser = dbUser;
		this.dbPassword = dbPassword;

		connect();
	}

	// provides URL for connecting to database, and initializes prepared statements
	private void connect() throws SQLException {
		final String url = String.format("jdbc:mysql://%s:%d/%s?user=%s&password=%s", dbHost, dbPort, dbName, dbUser,
				dbPassword);
		// DriveManger initializes connection to specified database
		this.connection = DriverManager.getConnection(url);

		this.listPokemon = this.connection.prepareStatement(LIST_POKEMON);

		this.searchById = this.connection.prepareStatement(SEARCH_BY_ID);
		this.searchByIdAdmin = this.connection.prepareStatement(SEARCH_BY_ID_ADMIN);
		this.searchByName = this.connection.prepareStatement(SEARCH_BY_NAME);
		this.searchByLetter = this.connection.prepareStatement(SEARCH_BY_LETTER);
		this.searchByType = this.connection.prepareStatement(SEARCH_BY_TYPE);
		this.searchByDualType = this.connection.prepareStatement(SEARCH_DUAL_TYPE);
		
		this.getHealth = this.connection.prepareStatement(GET_HEALTH);
		this.getEntryAdmin = this.connection.prepareStatement(GET_ENTRY_ADMIN);
		this.getEntry = this.connection.prepareStatement(GET_ENTRY);
		this.getTypeId = this.connection.prepareStatement(GET_TYPE_ID);

		this.insertType = this.connection.prepareStatement(INSERT_TYPE);
		this.insertStats = this.connection.prepareStatement(INSERT_STATS);

		this.resetTypes = this.connection.prepareStatement(RESET_TYPES);
		this.resetStats = this.connection.prepareStatement(RESET_STATS);

		this.wasCaught = this.connection.prepareStatement(WAS_CAUGHT);
		this.seenPokemon = this.connection.prepareStatement(SEEN_POKEMON);
		this.addToDex = this.connection.prepareStatement(ADD_TO_DEX);
		this.remove = this.connection.prepareStatement(REMOVE);
	}

	// runs the application's interactive mode
	public void runApp() throws SQLException, InterruptedException {

		Random rand = new Random(); // for calculating catch rate later on
		in = new Scanner(System.in);// initialize general scanner
		String input; 				// to save user input, initialize with in.nextLine();
		int i; 						// commonly used to save inputted pokémon ID
		String pokemon; 			// used to save pokémon name based on ID
		String type; 				// used to save specified type name
		boolean printDex = true; 	// initialize option to print all pokémon in pokédex
		boolean caught = false; 	// variable to catch if pokémon was previously caught (probably more efficient
									// way to do this)
		boolean release = true; 	// variable to double-check if user REALLY wants to release a pokémon (probably
									// replaceable using break)

		// loop to trap user until they choose exit
		while (true) {
			// loop option to print all pokémon in pokédex
			while (printDex) {
				listPokemon();
				printDex = false;
			}
			// main dialogue
			System.out.print("\nWelcome to the Pokédex!\n\nWould you like to:\n"
					+ "   S) Search for a pokémon\n"
					+ "   C) Attempt to catch a pokémon\n" 
					+ "   O) Open a pokémon entry\n"
					+ "   R) Release a pokémon\n" 
					+ "   E) Exit\n");
			input = in.nextLine();

			// open search dialogue
			if (input.equalsIgnoreCase("S")) {
				Boolean searching = true;
				// loop to catch invalid input
				while (searching) {
					System.out.println("Would you like to:\n" 
							+ "   1) Search by ID\n" 
							+ "   2) Search by name\n"
							+ "   3) Search by letter\n" 
							+ "   4) Search by type\n" 
							+ "   5) Search by base stats\n"
							+ "(please enter an integer)\n");
					input = in.nextLine();
					// open search by ID dialogue
					if (input.equalsIgnoreCase("1")) {
						System.out.println("Please enter the ID/s of the pokémon you want to search:\n"
								+ "(Enter an integer or series of integers using commas to separate them)\n");
						input = in.nextLine();
						parse = new Scanner(input);
						parse.useDelimiter(",");
						System.out.println("\n");
						Boolean trap = true;
						try {
							while (parse.hasNext()) {
								i = Integer.parseInt(parse.next().trim());
								// check valid ID
								if (i > 0 && i < 494) {
									pokemon = searchById(i);
									if (pokemon.equals("???")) {
									} else {
										System.out.println(i + ": " + pokemon);
									}
								} else {
									System.out.println(i + " is an invalid ID.");
								}
							}
						}
						catch(NumberFormatException e){
							System.out.println("Invalid integer value.");
							trap = false;
						}
						while (trap) {
							System.out.println("Would you like to continue searching? (y/n)");
							input = in.nextLine();
							if (input.equalsIgnoreCase("n")) {
								trap = false;
								searching = false;
							} else if (input.equalsIgnoreCase("y")) {
								trap = false;
							} else {
								System.out.println("\"y\" or \"n\" only.");
							}
						}
					}
					// open search by name dialogue
					else if (input.equalsIgnoreCase("2")) {
						System.out.println("Please enter the name/s of the pokémon you want to search:\n"
								+ "(Enter with commas to separate multiple names)\n");
						input = in.nextLine();
						parse = new Scanner(input);
						parse.useDelimiter(",");
						System.out.println("\n");
						while (parse.hasNext()) {
							pokemon = parse.next().trim();
							searchByName(pokemon);
						}
						Boolean trap = true;
						while (trap) {
							System.out.println("Would you like to continue searching? (y/n)");
							input = in.nextLine();
							if (input.equalsIgnoreCase("n")) {
								trap = false;
								searching = false;
							} else if (input.equalsIgnoreCase("y")) {
								trap = false;
							} else {
								System.out.println("\"y\" or \"n\" only.");
							}
						}
					} else if (input.equalsIgnoreCase("3")) {
						System.out.println("Please enter the letter/s of the pokémon you want to search:\n"
								+ "Remember! You can only search pokémon in your pokédex!\n"
								+ "(Enter with commas to separate multiple letters)\n");
						input = in.nextLine();
						parse = new Scanner(input);
						parse.useDelimiter(",");
						System.out.println("\n");
						while (parse.hasNext()) {
							pokemon = parse.next().trim();
							searchByLetter(pokemon);
						}
						Boolean trap = true;
						while (trap) {
							System.out.println("Would you like to continue searching? (y/n)");
							input = in.nextLine();
							if (input.equalsIgnoreCase("n")) {
								trap = false;
								searching = false;
							} else if (input.equalsIgnoreCase("y")) {
								trap = false;
							} else {
								System.out.println("\"y\" or \"n\" only.");
							}
						}
					} else if (input.equalsIgnoreCase("4")) {
						System.out.println("Would you like to search by single or dual types?\n"
								+ "(Please enter \"single\" or \"dual\".)\n");
						input = in.nextLine();
						if (input.equalsIgnoreCase("dual")) {
							System.out.println("Pleaser enter the dual type you would like to search:\n"
									+ "Remember! You will only see pokémon from your pokédex!\n"
									+ "(Enter with a space in between. Ex: \"normal fighting\"\n"
									+ "use commas to separate multiple dual type inqueries)\n");
							input = in.nextLine();
							parse = new Scanner(input);
							parse.useDelimiter(",");
							ArrayList<String> str = new ArrayList<String>();
							int count = 0;
							while (parse.hasNext()) {
								str.add(parse.next());
								inception = new Scanner(str.get(count));
								inception.useDelimiter(" ");
								ArrayList<String> string = new ArrayList<String>();
								while (inception.hasNext()) {
									string.add(inception.next());
								}
								if (string.size() != 2) {
									System.out.println("Invalid dual type.");
								} else {
									searchByDualType(string.get(0).trim(), string.get(1).trim());
								}
								count++;
							}
						} else if (input.equalsIgnoreCase("single")) {
							System.out.println("Pleaser enter the name of the type you would like to search:\n\n"
									+ "Remember! You will only see pokémon from your pokédex!\n\n"
									+ "(Enter with commas to separate multiple type names)\n");
							input = in.nextLine();
							parse = new Scanner(input);
							parse.useDelimiter(",");
							while (parse.hasNext()) {
								type = parse.next().trim();
								System.out.println("\n...Searching for " + type + " types...\n");
								searchByType(type);
							}
						}
						else {
							System.out.println("Invalid input, \"single\" or \"dual\" only.");
						}
						Boolean trap = true;
						while (trap) {
							System.out.println("Would you like to continue searching? (y/n)");
							input = in.nextLine();
							if (input.equalsIgnoreCase("n")) {
								trap = false;
								searching = false;
							} else if (input.equalsIgnoreCase("y")) {
								trap = false;
							} else {
								System.out.println("\"y\" or \"n\" only.");
							}
						}
					} else if (input.equalsIgnoreCase("5")) {
						System.out.println("Please enter the stat, mathematical symbol, and number you would like to search.\n"
								+ "List of stats: hp, atk, def, satk, sdef, spd, total\n"
								+ "List of mathematical symbols: >, <, =\n"
								+ "(Ex: total > 600)");
						input = in.nextLine();
						parse = new Scanner(input);
						parse.useDelimiter(" ");
						ArrayList<String> str = new ArrayList<String>();
						while(parse.hasNext()) {
							str.add(parse.next().trim());
						}
						if(str.size() == 3) {
							if(str.get(0).equalsIgnoreCase("hp") || str.get(0).equalsIgnoreCase("atk") || str.get(0).equalsIgnoreCase("def")
									|| str.get(0).equalsIgnoreCase("satk") || str.get(0).equalsIgnoreCase("sdef") || str.get(0).equalsIgnoreCase("spd")
									|| str.get(0).equalsIgnoreCase("total")) {
								if(str.get(1).equalsIgnoreCase(">")) {
									try {
										if(Integer.parseInt(str.get(2)) > 0) {
											i = 1;
											String stat = str.get(0).toUpperCase();
											PokeStats statName = PokeStats.valueOf(stat);
											System.out.println("\n...Loading...\n");
											ArrayList<String> listPokemon = new ArrayList<String>();
											String prntLine;
											while(i < 494) {
												int[] stats = getStats(i);
												if(stats[(statName.ordinal()+1)] > Integer.parseInt(str.get(2))) {
													prntLine = i + ": " + searchByIdAdmin(i) + " " + stat.toLowerCase() + " = "  + stats[(statName.ordinal()+1)];
													listPokemon.add(prntLine);
												}
												i++;
											}
											if(listPokemon.isEmpty()) {
												System.out.println("No results.\n");
											} else {
												Iterator<String> itr = listPokemon.iterator();
												while(itr.hasNext()) {
													prntLine = itr.next();
													System.out.println(prntLine);
												}
											}
										}
										else {
											System.out.println("Invalid number.");
										}
									}
									catch(NumberFormatException e){
										System.out.println("Invalid integer value.");
									}
								}
								else if(str.get(1).equalsIgnoreCase("<")) {
									try {
										if(Integer.parseInt(str.get(2)) > 0) {
											i = 1;
											String stat = str.get(0).toUpperCase();
											PokeStats statName = PokeStats.valueOf(stat);
											System.out.println("\n...Loading...\n");
											ArrayList<String> listPokemon = new ArrayList<String>();
											String prntLine;
											while(i < 494) {
												int[] stats = getStats(i);
												if(stats[(statName.ordinal()+1)] < Integer.parseInt(str.get(2)) && stats[(statName.ordinal()+1)] != 0) {
													prntLine = i + ": " + searchByIdAdmin(i) + " " + stat.toLowerCase() + " = "  + stats[(statName.ordinal()+1)];
													listPokemon.add(prntLine);
												}
												i++;
											}
											if(listPokemon.isEmpty()) {
												System.out.println("No results.\n");
											} else {
												Iterator<String> itr = listPokemon.iterator();
												while(itr.hasNext()) {
													prntLine = itr.next();
													System.out.println(prntLine);
												}
											}								}
										else {
											System.out.println("Invalid number.");
										}
									}
									catch(NumberFormatException e){
										System.out.println("Invalid integer value.");
									}
								}
								else if(str.get(1).equalsIgnoreCase("=")) {
									try {
										if(Integer.parseInt(str.get(2)) > 0) {
											i = 1;
											String stat = str.get(0).toUpperCase();
											PokeStats statName = PokeStats.valueOf(stat);
											System.out.println("\n...Loading...\n");
											ArrayList<String> listPokemon = new ArrayList<String>();
											String prntLine;
											while(i < 494) {
												int[] stats = getStats(i);
												if(stats[(statName.ordinal()+1)] == Integer.parseInt(str.get(2))) {
													prntLine = i + ": " + searchByIdAdmin(i) + " " + stat.toLowerCase() + " = "  + stats[(statName.ordinal()+1)];
													listPokemon.add(prntLine);
												}
												i++;
											}
											if(listPokemon.isEmpty()) {
												System.out.println("No results.\n");
											} else {
												Iterator<String> itr = listPokemon.iterator();
												while(itr.hasNext()) {
													prntLine = itr.next();
													System.out.println(prntLine);
												}
											}								}
										else {
											System.out.println("Invalid number.");
										}
									}
									catch(NumberFormatException e){
										System.out.println("Invalid integer value.");
									}
								}
								else {
									System.out.println("Invalid mathematical symbol.");
								}
							}
							else {
								System.out.println("Invalid stat name.");
							}
						}
						
						Boolean trap = true;
						while (trap) {
							System.out.println("Would you like to continue searching? (y/n)");
							input = in.nextLine();
							if (input.equalsIgnoreCase("n")) {
								trap = false;
								searching = false;
							} else if (input.equalsIgnoreCase("y")) {
								trap = false;
							} else {
								System.out.println("\"y\" or \"n\" only.");
							}
						}
					} else {
						System.out.println("Please enter a valid input.\n");
					}
				}
			}

			// open attempt-to-catch dialogue
			else if (input.equalsIgnoreCase("C")) {
				System.out.println(
						"Enter the id of the pokémon you are attempting to catch!\n" + "(please enter an integer):");
				input = in.nextLine();
				try {
					i = Integer.parseInt(input);
					System.out.println("...Searching for the pokémon with the ID " + i + "...\n");
					// check if valid ID
					if (i > 0 && i < 494) {
						pokemon = searchByIdAdmin(i);
						caught = wasCaught(i);
						// check if pokémon was previously caught
						if (caught == true) {
							System.out.println("It seems you've already caught a " + pokemon
									+ ". We don't want them to go extinct!\n");
						} else if (caught == false) {
							System.out.println("Attempting to catch " + pokemon + "!");
							Thread.sleep(1000);
							int pokeball = rand.nextInt(151);
							attemptCatch(i, pokeball);
						}
					} else {
						System.out.println(i + " is an invalid ID.");
					}
				}
				catch(NumberFormatException e) {
					System.out.println("Invalid integer value.");
				}
				
				System.out.println("\nWould you like to load the dex? (y/n)");
				input = in.nextLine();
				while(true) {
					if (input.equalsIgnoreCase("y")) {
						printDex = true;
						break;
					}
					else if(input.equalsIgnoreCase("n")){
						break;
					}
					else {
						System.out.println("y/n only.");
						input = in.nextLine();
					}
				}
				
			}

			// open entry dialogue
			else if (input.equalsIgnoreCase("O")) {
				Boolean openEntries = true;
				while (openEntries) {
					System.out.println("Please enter the ID of the pokémon entry you would like to open.\n"
							+ "(please enter an integer):");
					boolean trap = true;
					try {
						i = Integer.parseInt(in.nextLine().trim());
						if (i < 1 || i > 494) {
							System.out.println("Invalid ID.");
						} else {
							getEntry(i);
						}
					}
					catch (NumberFormatException e){
						System.out.println("Must input an integer.");
						trap = false;
					}
					while (trap) {
						System.out.println("Would you like to open another entry? (y/n)");
						input = in.nextLine();
						if (input.equalsIgnoreCase("n")) {
							openEntries = false;
							trap = false;
						} else if (input.equalsIgnoreCase("y")) {
							trap = false;
						} else {
							System.out.println("\"y\" or \"n\" only.");
						}
					}
				}
			}

			// open release dialogue
			else if (input.equalsIgnoreCase("R")) {
				int looped = 0;
				System.out.println("Enter the id of the pokémon you would like to release.\n"
						+ "(please enter an integer, or series of integers using commas to separate them):");
				input = in.nextLine();
				parse = new Scanner(input);
				parse.useDelimiter(",");
				try {
					while (parse.hasNext()) {
						i = Integer.parseInt(parse.next().trim());
						System.out.println("\n...Searching for the pokémon with the ID " + i + "...\n");
						// loop in order to account for unsure decisions from user input
						looped = 0;
						release = true;
						while (release) {
							// check if valid ID
							if (i > 0 && i < 494) {
								pokemon = searchById(i);
								if(pokemon.equals("???")) {
									System.out.println("There is no pokémon to release!");
									break;
								}
								if (looped < 1) {
									System.out.println("Are you sure you want to release " + pokemon + "? (y/n)");
								} else {
									System.out.println("Answer \"y\" or \"n\" only.");
								}
								input = in.nextLine();
								if (input.equalsIgnoreCase("y")) {
									System.out.println("As you release " + pokemon + ", your memory fades just as "
											+ pokemon + " fades into the grass...");
									remove(i);
									Thread.sleep(1500);
									release = false; // *does break do the same thing?
								} else if (input.equalsIgnoreCase("n")) {
									System.out.println("Very well. " + pokemon + " is uneasy, but grateful.");
									release = false;
								} else {
									System.out.println("This is serious, do you want to release " + pokemon + "?");
									looped++;
								}
							} else {
								System.out.println(i + " is an invalid ID.");
								release = false;
							}
						}
					}
				}
				catch(NumberFormatException e) {
					System.out.println("Invalid integer value.");
				}
				System.out.println("\nWould you like to load the dex? (y/n)");
				input = in.nextLine();
				while(true) {
					if (input.equalsIgnoreCase("y")) {
						printDex = true;
						break;
					}
					else if(input.equalsIgnoreCase("n")){
						break;
					}
					else {
						System.out.println("y/n only.");
						input = in.nextLine();
					}
				}
			}

			// open exit
			else if (input.equalsIgnoreCase("E")) {
				break;
			}

			// admin reset pokédex to include all pokémon
			else if (input.equalsIgnoreCase("admin reset")) {
				int id = 1;
				while (id < 494) {
					addToDex(id);
					System.out.println("Added " + searchById(id));
					id++;
				}
				System.out.println("\nReset complete");
				Thread.sleep(1500);
			}

			// admin insert type function
			else if (input.equalsIgnoreCase("admin insert type")) {
				// from https://www.javatpoint.com/how-to-read-excel-file-in-java
				resetTypes(); // need to delete data in HasTypes to reset
				uglyInsertTypeMethod(); // makes runApp() less ugly and long
			}

			// admin insert base stats function
			else if (input.equalsIgnoreCase("admin insert stats")) {
				resetStats();
				uglyInsertStatsMethod();
			}
			// open "invalid input" dialogue
			else {
				System.out.println("Invalid input.");
			}
		}
	}

	// returns list of all pokémon in the pokédex by generation
	public void listPokemon() throws SQLException {
		System.out.println("\n...Loading Pokédex...");
		System.out.println("\n  Gen I:\n");
		listPokemon.setInt(1, 1);
		ResultSet results = listPokemon.executeQuery();
		while (results.next()) {
			String id = results.getString("ID");
			String name = results.getString("name");
			if (name == null) {
				System.out.println(id + ": " + "???");
			} else {
				System.out.println(id + ": " + name);
			}
		}
		System.out.println("\n  Gen II:\n");
		listPokemon.setInt(1, 2);
		results = listPokemon.executeQuery();
		while (results.next()) {
			String id = results.getString("id");
			String name = results.getString("name");
			if (name == null) {
				System.out.println(id + ": " + "???");
			} else {
				System.out.println(id + ": " + name);
			}
		}
		System.out.println("\n  Gen III:\n");
		listPokemon.setInt(1, 3);
		results = listPokemon.executeQuery();
		while (results.next()) {
			String id = results.getString("id");
			String name = results.getString("name");
			if (name == null) {
				System.out.println(id + ": " + "???");
			} else {
				System.out.println(id + ": " + name);
			}
		}
		System.out.println("\n  Gen IV:\n");
		listPokemon.setInt(1, 4);
		results = listPokemon.executeQuery();
		while (results.next()) {
			String id = results.getString("id");
			String name = results.getString("name");
			if (name == null) {
				System.out.println(id + ": " + "???");
			} else {
				System.out.println(id + ": " + name);
			}
		}
	}

	// returns name of the pokémon with the specified ID
	public String searchById(int id) throws SQLException {
		String name = null;
		String oops = "???";
		searchById.setInt(1, id);
		ResultSet results = searchById.executeQuery();
		while (results.next()) {
			name = results.getString("name");
		}
		if (name == null) {
			System.out.println("\nThere is no information on the pokémon with ID " + id);
		} else {
			return name;
		}
		return oops;
	}

	// returns name of the pokémon with the specified ID, for admin use
	public String searchByIdAdmin(int id) throws SQLException {
		String name = null;
		searchByIdAdmin.setInt(1, id);
		ResultSet results = searchByIdAdmin.executeQuery();
		while (results.next()) {
			name = results.getString("name");
		}
		return name;
	}

	// returns pokémon by their name
	public void searchByName(String name) throws SQLException {
		int id = 0;
		String output = "";
		searchByName.setString(1, name);
		ResultSet results = searchByName.executeQuery();
		while (results.next()) {
			id = results.getInt("ID");
			output = results.getString("name");
		}
		if (id == 0) {
			System.out.println("\"" + name + "\"" + " is an invalid name.\n");
		} else {
			System.out.println(id + ": " + output);
		}
	}

	// returns pokémon if they contain the specified letter in their name
	public void searchByLetter(String letter) throws SQLException {
		int id = 0;
		String output = "";
		searchByLetter.setString(1, "%" + letter + "%");
		ResultSet results = searchByLetter.executeQuery();
		if(!results.isBeforeFirst()) {
			System.out.println("There are no pokémon whose names include \"" + letter + "\":");
		}else {
			System.out.println("\nPokémon whose names include \"" + letter + "\":");
			while (results.next()) {
				id = results.getInt("ID");
				output = results.getString("name");
				if (id == 0) {
					System.out.println(letter + "is an invalid character.\n");
				} else if (output == null) {
				} else {
					System.out.println(id + ": " + output);
				}
			}
		}
	}

	// returns a list of pokémon if they have the specified type
	public void searchByType(String type) throws SQLException {
		int id = 0;
		String pokemon = "";
		if (type.equalsIgnoreCase("Normal") || type.equalsIgnoreCase("Fire") || type.equalsIgnoreCase("Water")
				|| type.equalsIgnoreCase("Grass") || type.equalsIgnoreCase("Electric") || type.equalsIgnoreCase("Ice")
				|| type.equalsIgnoreCase("Fighting") || type.equalsIgnoreCase("Poison")
				|| type.equalsIgnoreCase("Ground") || type.equalsIgnoreCase("Flying")
				|| type.equalsIgnoreCase("Psychic") || type.equalsIgnoreCase("Bug") || type.equalsIgnoreCase("Rock")
				|| type.equalsIgnoreCase("Ghost") || type.equalsIgnoreCase("Dark") || type.equalsIgnoreCase("Dragon")
				|| type.equalsIgnoreCase("Steel") || type.equalsIgnoreCase("Fairy")) {
			searchByType.setString(1, type);
			ResultSet results = searchByType.executeQuery();
			while (results.next()) {
				id = results.getInt("ID");
				pokemon = results.getString("name");
				if (pokemon == null) {
				} else {
					System.out.println(id + ": " + pokemon);
				}
			}

		} else {
			System.out.println("Invalid type name.\n");
		}

	}

	// returns a list of pokémon if they have the specified dual type
	public void searchByDualType(String type, String type2) throws SQLException {
		int id = 0;
		String pokemon = "";
		String first = "";
		String second = "";
		Boolean check = false;
		Boolean doubleCheck = true;
		if (type.equalsIgnoreCase("Normal") || type.equalsIgnoreCase("Fire") || type.equalsIgnoreCase("Water")
				|| type.equalsIgnoreCase("Grass") || type.equalsIgnoreCase("Electric") || type.equalsIgnoreCase("Ice")
				|| type.equalsIgnoreCase("Fighting") || type.equalsIgnoreCase("Poison")
				|| type.equalsIgnoreCase("Ground") || type.equalsIgnoreCase("Flying")
				|| type.equalsIgnoreCase("Psychic") || type.equalsIgnoreCase("Bug") || type.equalsIgnoreCase("Rock")
				|| type.equalsIgnoreCase("Ghost") || type.equalsIgnoreCase("Dark") || type.equalsIgnoreCase("Dragon")
				|| type.equalsIgnoreCase("Steel")
				|| type.equalsIgnoreCase("Fairy") && (type2.equalsIgnoreCase("Normal") || type2.equalsIgnoreCase("Fire")
						|| type2.equalsIgnoreCase("Water") || type2.equalsIgnoreCase("Grass")
						|| type2.equalsIgnoreCase("Electric") || type2.equalsIgnoreCase("Ice")
						|| type2.equalsIgnoreCase("Fighting") || type2.equalsIgnoreCase("Poison")
						|| type2.equalsIgnoreCase("Ground") || type2.equalsIgnoreCase("Flying")
						|| type2.equalsIgnoreCase("Psychic") || type2.equalsIgnoreCase("Bug")
						|| type2.equalsIgnoreCase("Rock") || type2.equalsIgnoreCase("Ghost")
						|| type2.equalsIgnoreCase("Dark") || type2.equalsIgnoreCase("Dragon")
						|| type2.equalsIgnoreCase("Steel") || type2.equalsIgnoreCase("Fairy"))) {
			searchByDualType.setString(1, type2);
			searchByDualType.setString(2, type);
			ResultSet results = searchByDualType.executeQuery();
			if (!results.isBeforeFirst()) {
				System.out.println("No results for " + type + ", " + type2);
			} else if (type.equalsIgnoreCase(type2)) {
				System.out.println("Types must be different.");
			} else {
				while (results.next()) {
					id = results.getInt("ID");
					pokemon = results.getString("name");
					first = results.getString("firstType");
					second = results.getString("secondType");
					if (pokemon == null || id < 1 || id > 494) {
					}
					/*
					 * the way the pokedex works is that Rotom and Wormadam have one unique ID
					 * number, but they have multiple forms that have different type combinations.
					 * My database does not account for this, therefore this logic helps weed out
					 * combinations that are false, and keep combinations of Rotom and Wormadam that
					 * are true.
					 */
					else if (pokemon.equalsIgnoreCase("Rotom")
							&& (!type.equalsIgnoreCase("Electric") && !type2.equalsIgnoreCase("Electric"))
							|| pokemon.equalsIgnoreCase("Wormadam")
									&& (!type.equalsIgnoreCase("Bug") && !type2.equalsIgnoreCase("Bug"))) {
						check = true;
					} else {
						System.out.println(id + ": " + pokemon + " (" + first + ", " + second + ")");
						doubleCheck = false;
					}
				}
				if (check && doubleCheck) {
					System.out.println("No results for " + type + ", " + type2);
				}
			}
		} else {
			System.out.println("Invalid dual type name.\n");
		}
	}
	
	// retrieve a pokémon's health by ID number
	public int getHealth(int id) throws SQLException {
		int hp = 1;
		getHealth.setInt(1, id);
		ResultSet results = getHealth.executeQuery();
		while (results.next()) {
			hp = results.getInt("hp");
		}
		return hp;
	}
	
	// gets pokemon stats
	public int[] getStats(int id) throws SQLException {
		int[] stats =  new int[8];
		int seen;
		int caught;
		getEntry.setInt(1, id);
		ResultSet results = getEntry.executeQuery();
		if(results.next()) {
			seen = results.getInt("seen");
			caught = results.getInt("caught");
			stats[0] = id;
			stats[1] = results.getInt("hp");
			stats[2] = results.getInt("atk");
			stats[3] = results.getInt("def");
			stats[4] = results.getInt("satk");
			stats[5] = results.getInt("sdef");
			stats[6] = results.getInt("spd");
			stats[7] = results.getInt("total");
			if(seen == 1 && caught != 1) {
				stats = new int[8];
			}
			else if(caught == 1) {
			}
			else {
				stats = new int[8];
			}
		} else {
			System.out.println("Invalid request.");
		}
		return stats;		
	}
	
	// get data and logic necessary for a pokémon entry
	public void getEntry(int id) throws SQLException {
		String pokemon;
		int seen;
		int caught;
		String type;
		String type2 = "";
		int i;
		int hp;
		int atk;
		int def;
		int satk;
		int sdef;
		int spd;
		int total;
		getEntryAdmin.setInt(1, id);
		ResultSet results = getEntryAdmin.executeQuery();
		if (results.next()) {
			pokemon = results.getString("name");
			seen = results.getInt("seen");
			caught = results.getInt("caught");
			type = results.getString("type");
			i = results.getInt("id");
			hp = results.getInt("hp");
			atk = results.getInt("atk");
			def = results.getInt("def");
			satk = results.getInt("satk");
			sdef = results.getInt("sdef");
			spd = results.getInt("spd");
			total = results.getInt("total");
			if (results.next()) {
				type2 = results.getString("type");
				if (seen == 1 && caught != 1) {
					System.out.println(i + ":	" + pokemon + "\n" + "type:	???\n" + "hp:	???\n" + "atk:	???\n"
							+ "def:	???\n" + "satk:	???\n" + "sdef:	???\n" + "spd:	???\n" + "total:	???\n");
				} else if (caught == 1) {
					System.out.println(i + ":	" + pokemon + "\n" + "type:	" + type + ", " + type2 + "\n" + "hp:	"
							+ hp + "\n" + "atk:	" + atk + "\n" + "def:	" + def + "\n" + "satk:	" + satk + "\n"
							+ "sdef:	" + sdef + "\n" + "spd:	" + spd + "\n" + "total:	" + total + "\n");
				} else {
					System.out.println(i + ":	???\n" + "type:	???\n" + "hp:	???\n" + "atk:	???\n" + "def:	???\n"
							+ "satk:	???\n" + "sdef:	???\n" + "spd:	???\n" + "total:	???\n");
				}
			} else {
				if (seen == 1 && caught != 1) {
					System.out.println(i + ":	" + pokemon + "\n" + "type:	???\n" + "hp:	???\n" + "atk:	???\n"
							+ "def:	???\n" + "satk:	???\n" + "sdef:	???\n" + "spd:	???\n" + "total:	???\n");
				} else if (caught == 1) {
					System.out.println(i + ":	" + pokemon + "\n" + "type:	" + type + "\n" + "hp:	" + hp + "\n"
							+ "atk:	" + atk + "\n" + "def:	" + def + "\n" + "satk:	" + satk + "\n" + "sdef:	" + sdef
							+ "\n" + "spd:	" + spd + "\n" + "total:	" + total + "\n");
				} else {
					System.out.println(i + ":	???\n" + "type:	???\n" + "hp:	???\n" + "atk:	???\n" + "def:	???\n"
							+ "satk:	???\n" + "sdef:	???\n" + "spd:	???\n" + "total:	???\n");
				}
			}
		} else {
			System.out.println("Invalid request.");
		}

	}

	// get type ID based on type name
	public int getTypeId(String name) throws SQLException {
		int id = 0;
		getTypeId.setString(1, name);
		ResultSet results = getTypeId.executeQuery();
		while (results.next()) {
			id = results.getInt("ID");
		}
		return id;
	}

	// inserts type into database
	public void insertType(int typeId, int id) throws SQLException {
		if (id == 0) {
			System.out.println("Invalid id.\n");
		} else if (typeId == 0) {
			System.out.println("Invalid type ID.\n");
		} else {
			insertType.setInt(1, typeId);
			insertType.setInt(2, id);
			insertType.execute();
		}

	}

	// inserts stats into database
	public void insertStats(int id, int hp, int atk, int def, int satk, int sdef, int spd, int total)
			throws SQLException {
		if (id < 1 || id > 493) {
			System.out.println("Invalid id.\n");
		} else {
			insertStats.setInt(1, id);
			insertStats.setInt(2, hp);
			insertStats.setInt(3, atk);
			insertStats.setInt(4, def);
			insertStats.setInt(5, satk);
			insertStats.setInt(6, sdef);
			insertStats.setInt(7, spd);
			insertStats.setInt(8, total);
			insertStats.execute();
		}
	}

	// deletes HasType data for reset
	public void resetTypes() throws SQLException {
		resetTypes.execute();
	}

	// deletes BaseStatSet data for reset
	public void resetStats() throws SQLException {
		resetStats.execute();
	}

	// determines if pokémon was caught previously using ID
	public boolean wasCaught(int id) throws SQLException {
		wasCaught.setInt(1, id);
		boolean output = true;
		ResultSet results = wasCaught.executeQuery();
		while (results.next()) {
			output = results.getBoolean("caught");
		}
		return output;
	}

	// simulates an attempt to catch a pokémon using a VERY simplified formula
	public void attemptCatch(int id, int theta) throws SQLException {
		// formula from https://pokemon.fandom.com/wiki/Catch_Rate#Formulae, GEN I
		String input;
		int hitpoints = getHealth(id);
		double alpha = (hitpoints * 255 * 4) / (hitpoints * 12);
		if (theta >= alpha) {
			System.out.println("The catch was successful!\n");
			addToDex(id);
		} else {
			seenPokemon(id);
			System.out.println("The catch was unsuccessful. Would you like to try again? (y/n)\n");
			input = in.nextLine();
			if (input.equalsIgnoreCase("y")) {
				Random rand = new Random();
				attemptCatch(id, rand.nextInt(201));
			}
		}

	}

	// failed to catch pokemon, but now you've seen it, and it's name is entered
	// into the pokedex!
	public void seenPokemon(int id) throws SQLException {
		seenPokemon.setInt(1, id);
		seenPokemon.execute();
	}

	// updates pokédex if trainer successfully catches pokémon
	public void addToDex(int id) throws SQLException {
		addToDex.setInt(1, id);
		addToDex.execute();
	}

	// release a pokémon and erase all memory of the respective species
	public void remove(int id) throws SQLException {
		remove.setInt(1, id);
		remove.execute();
	}

	// method defined for reading a cell
	// from https://www.javatpoint.com/how-to-read-excel-file-in-java
	public String ReadCellData(int vRow, int vColumn) {
		String value = null; // variable for storing the cell value
		Workbook wb = null; // initialize Workbook null
		try {
			// reading data from a file in the form of bytes
			FileInputStream fis = new FileInputStream("C:\\Users\\18018\\Downloads\\Excel Pkdx V5.14.xlsx");
			// constructs an XSSFWorkbook object, by buffering the whole stream into the
			// memory
			wb = new XSSFWorkbook(fis);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		Sheet sheet = wb.getSheetAt(0); // getting the XSSFSheet object at given index
		Row row = sheet.getRow(vRow); // returns the logical row
		Cell cell = row.getCell(vColumn); // getting the cell representing the given column
		if (cell == null) {
			return null;
		} else {
			value = cell.getStringCellValue(); // getting cell value
			return value; // returns the cell value
		}
	}

	// streamline function to insert types into database
	public void insertTypeLoop(int row, int id, int loop) throws SQLException {
		ReadCellExample rc = new ReadCellExample();
		String type1;
		int type1Id;
		String type2;
		int type2Id;
		while (row < loop) {
			type1 = rc.ReadCellData(row, 10);
			type1Id = getTypeId(type1);
			System.out.println(id + ": " + searchByIdAdmin(id) + "	first type:	" + type1);
			insertType(id, type1Id);
			type2 = rc.ReadCellData(row, 11);
			if (type2 == null) {
			} else {
				System.out.println(id + ": " + searchByIdAdmin(id) + "	second type:	" + type2);
				type2Id = getTypeId(type2);
				insertType(id, type2Id);
			}
			id++;
			row++;
		}
	}

	// streamline function to insert stats into database
	public void insertStatsLoop(int row, int loop) throws SQLException {
		ReadCellExample rc = new ReadCellExample();
		String stat;
		String id;
		Scanner parse;
		int i;
		ArrayList<Integer> str;
		while (row < loop) {
			str = new ArrayList<Integer>();
			id = rc.ReadCellData(row, 1);
			parse = new Scanner(id);
			parse.useDelimiter("\\.");
			str.add(Integer.parseInt(parse.next())); // id in index 0
			i = 3;
			while (i < 10) {
				stat = rc.ReadCellData(row, i);
				parse = new Scanner(stat);
				parse.useDelimiter("\\.");
				str.add(Integer.parseInt(parse.next())); // stats, one-by-one until total in index 7
				i++;
			}
			System.out.println(str.get(0).intValue() + ": " + searchByIdAdmin(str.get(0).intValue()) + " " + str.get(1).intValue()
					+ " " + str.get(2).intValue() + " " + str.get(3).intValue() + " " + str.get(4).intValue() + " "
					+ str.get(5).intValue() + " " + str.get(6).intValue() + " " + str.get(7).intValue());
			insertStats(str.get(0).intValue(), str.get(1).intValue(), str.get(2).intValue(), str.get(3).intValue(),
					str.get(4).intValue(), str.get(5).intValue(), str.get(6).intValue(), str.get(7).intValue());
			row++;
		}
	}

	// makes runApp() less ugly, runs admin insert base stats function
	public void uglyInsertStatsMethod() throws SQLException {
		// ReadCellExample rc = new ReadCellExample();		
		int row = 1;
		insertStatsLoop(row, 387);
		row = 390; // skip deoxys forms, start at turtwig
		insertStatsLoop(row, 417);
		row = 419; // skip wormadam forms, start at mothim
		insertStatsLoop(row, 485); // stop at rotom
		row = 490; // skip to uxie
		insertStatsLoop(row, 498); // stop at giratina
		row = 499;
		insertStatsLoop(row, 504);
		row = 505; // skip shaymin sky form
		insertStatsLoop(row, 506); // stop at arceus
		
	}

	// just makes runApp() a little cuter, runs admin insert type function
	public void uglyInsertTypeMethod() throws SQLException {
		ReadCellExample rc = new ReadCellExample();
		int row = 1, id = 1;
		String type1;
		int type1Id;
		String type2;
		int type2Id;
		insertTypeLoop(row, id, 387);
		id = 387;
		row = 390; // skipping deoxys forms
		insertTypeLoop(row, id, 416);
		id = 413;
		row = 416;
		type1 = rc.ReadCellData(row, 10);
		type1Id = getTypeId(type1);
		insertType(id, type1Id);
		System.out.println(id + ": " + searchByIdAdmin(id) + "	first type:	" + type1);
		type2 = rc.ReadCellData(row, 11);
		type2Id = getTypeId(type2);
		insertType(id, type2Id);
		System.out.println(id + ": " + searchByIdAdmin(id) + "	Plant Form:	" + type2);
		row++;
		type2 = rc.ReadCellData(row, 11);
		System.out.println(id + ": " + searchByIdAdmin(id) + "	Sandy Form:	" + type2);
		type2Id = getTypeId(type2);
		insertType(id, type2Id);
		row++;
		type2 = rc.ReadCellData(row, 11);
		System.out.println(id + ": " + searchByIdAdmin(id) + "	Trash Form:	" + type2);
		type2Id = getTypeId(type2);
		insertType(id, type2Id);
		row = 419;
		id = 414;
		insertTypeLoop(row, id, 484);
		id = 479;
		row = 484;
		type1 = rc.ReadCellData(row, 10); // rotom electric
		type1Id = getTypeId(type1);
		System.out.println(id + ": " + searchByIdAdmin(id) + "	first type:	" + type1);
		insertType(id, type1Id);
		type2 = rc.ReadCellData(row, 11); // rotom ghost
		System.out.println(id + ": " + searchByIdAdmin(id) + "	second type:	" + type2);
		type2Id = getTypeId(type2);
		insertType(id, type2Id);
		row++;
		type2 = rc.ReadCellData(row, 11); // rotom heat
		System.out.println(id + ": " + searchByIdAdmin(id) + "	Heat Form:	" + type2);
		type2Id = getTypeId(type2);
		insertType(id, type2Id);
		row++;
		type2 = rc.ReadCellData(row, 11); // rotom wash
		System.out.println(id + ": " + searchByIdAdmin(id) + "	Wash Form:	" + type2);
		type2Id = getTypeId(type2);
		insertType(id, type2Id);
		row++;
		type2 = rc.ReadCellData(row, 11); // rotom frost
		System.out.println(id + ": " + searchByIdAdmin(id) + "	Frost Form:	" + type2);
		type2Id = getTypeId(type2);
		insertType(id, type2Id);
		row++;
		type2 = rc.ReadCellData(row, 11); // rotom spin
		System.out.println(id + ": " + searchByIdAdmin(id) + "	Spin Form:	" + type2);
		type2Id = getTypeId(type2);
		insertType(id, type2Id);
		row++;
		type2 = rc.ReadCellData(row, 11); // rotom cut
		System.out.println(id + ": " + searchByIdAdmin(id) + "	Cut Form:	" + type2);
		type2Id = getTypeId(type2);
		insertType(id, type2Id);
		row = 490;
		id = 480;
		insertTypeLoop(row, id, 498);
		id = 488;
		row = 499; // skip Giratina Origin form
		insertTypeLoop(row, id, 503);
		id = 492;
		row = 503;
		type1 = rc.ReadCellData(row, 10);
		type1Id = getTypeId(type1);
		System.out.println(id + ": " + searchByIdAdmin(id) + "	first type:	" + type1);
		insertType(id, type1Id);
		row++;
		type2 = rc.ReadCellData(row, 11);
		System.out.println(id + ": " + searchByIdAdmin(id) + "	Sky Form:	" + type2);
		type2Id = getTypeId(type2);
		insertType(id, type2Id);
		row++;
		id = 493;
		type1 = rc.ReadCellData(row, 10);
		type1Id = getTypeId(type1);
		System.out.println(id + ": " + searchByIdAdmin(id) + "	first type:	" + type1);
		insertType(id, type1Id);
	}

	public void close() throws Exception {
		connection.close();
	}

	public static void main(String... args) {
		// Default connection parameters (can be overridden on command line)
		Map<String, String> params = new HashMap<>(
				Map.of("dbname", "" + DB_NAME, "user", DB_USER, "password", DB_PASSWORD));
		boolean printHelp = false;
		// Parse command-line arguments, overriding values in params
		for (int i = 0; i < args.length && !printHelp; ++i) {
			String arg = args[i];
			boolean isLast = (i + 1 == args.length);

			switch (arg) {
			case "-h":
			case "-help":
				printHelp = true;
				break;

			case "-dbname":
			case "-user":
			case "-password":
				if (isLast)
					printHelp = true;
				else
					params.put(arg.substring(1), args[++i]);
				break;

			default:
				System.err.println("Unrecognized option: " + arg);
				printHelp = true;
			}
		}
		// If help was requested, print it and exit
		if (printHelp) {
			printHelp();
			return;
		}

		// Connect to the database. This use of "try" ensures that the database connection
		// is closed, even if an exception occurs while running the app.
		try (DatabaseTunnel tunnel = new DatabaseTunnel();
				Pokédex app = new Pokédex("localhost", tunnel.getForwardedPort(), params.get("dbname"),
						params.get("user"), params.get("password"))) {

			// Run the application
			try {
				app.runApp();
			} catch (SQLException ex) {
				System.err.println("\n\n=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-");
				System.err.println("SQL error when running database app!\n");
				ex.printStackTrace();
				System.err.println("\n\n=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-");
			}
		} catch (IOException ex) {
			System.err.println("Error setting up ssh tunnel.");
			ex.printStackTrace();
		} catch (SQLException ex) {
			System.err.println("Error communicating with the database (see full message below).");
			ex.printStackTrace();
			System.err.println("\nParameters used to connect to the database:");
			System.err.printf("\tSSH keyfile: %s\n\tDatabase name: %s\n\tUser: %s\n\tPassword: %s\n\n",
					params.get("sshkeyfile"), params.get("dbname"), params.get("user"), params.get("password"));
			System.err.println("(Is the MySQL connector .jar in the CLASSPATH?)");
			System.err.println("(Are the username and password correct?)");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void printHelp() {
		System.out.println("Accepted command-line arguments:");
		System.out.println();
		System.out.println("\t-help, -h          display this help text");
		System.out.println("\t-dbname <text>     override name of database to connect to");
		System.out.printf("\t                   (default: %s)\n", DB_NAME);
		System.out.println("\t-user <text>       override database user");
		System.out.printf("\t                   (default: %s)\n", DB_USER);
		System.out.println("\t-password <text>   override database password");
		System.out.println();
	}
}
