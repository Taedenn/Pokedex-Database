
//reading value of a particular cell  
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Scanner;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
/**
 * How to read from a xslx file From
 * https://www.javatpoint.com/how-to-read-excel-file-in-java
 */
public class ReadCellExample {
	public static void main(String[] args) {
		ReadCellExample rc = new ReadCellExample(); // object of the class
		int row = 1;
		String stat;
		String pokemon;
		String id;
		int intId;
		int intStat;
		Scanner parse;
		int i;
		while(row < 10) {
			id = rc.ReadCellData(row, 1);
			parse = new Scanner(id);
			parse.useDelimiter("\\.");
			intId = Integer.parseInt(parse.next());
			pokemon = rc.ReadCellData(row, 2);
			System.out.print(intId + ": " + pokemon + "	");
			i = 3;
			while(i < 10) {
				stat = rc.ReadCellData(row, i);
				parse = new Scanner(stat);
				parse.useDelimiter("\\.");
				intStat = Integer.parseInt(parse.next());
				if(i < 9) {
					System.out.print(getStatName(i) + ": " + intStat + ", ");
				}
				else {
					System.out.println(getStatName(i) + ": " + intStat);
				}
				i ++;
			}	
			row ++;
		}
		
	}
	
	public static String getStatName(int i) {
		String statName = "";
		switch(i) {
		case 3:
			statName = "hp";
			break;
		case 4:
			statName = "atk";
			break;
		case 5:
			statName = "def";
			break;
		case 6:
			statName = "satk";
			break;
		case 7:
			statName = "sdef";
			break;
		case 8:
			statName = "spd";
			break;
		case 9: 
			statName = "total";
			break;
		}
		return statName;
	}
	//method defined for reading a cell  
	@SuppressWarnings("deprecation")
	public String ReadCellData(int vRow, int vColumn) {
		String value = null; // variable for storing the cell value
		Workbook wb = null; // initialize Workbook null
		try {
			//reading data from a file in the form of bytes  
			FileInputStream fis = new FileInputStream("C:\\Users\\18018\\Downloads\\Excel Pkdx V5.14.xlsx");
			//constructs an XSSFWorkbook object, by buffering the whole stream into the memory  
			wb = new XSSFWorkbook(fis);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		Sheet sheet = wb.getSheetAt(0); // getting the XSSFSheet object at given index
		Row row = sheet.getRow(vRow); // returns the logical row
		Cell cell = row.getCell(vColumn); // getting the cell representing the given column
		if(cell == null) {
			return null;
		}
		else {
			cell.setCellType(Cell.CELL_TYPE_STRING);
			value = cell.getStringCellValue(); // getting cell value
			return value; // returns the cell value
		}
	}
}
