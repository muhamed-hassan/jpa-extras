package lib.persistence.repositories.exceptions;

public class DataNotFoundException extends RuntimeException {

	public DataNotFoundException() {
		super("Data not found");
	}	

}
