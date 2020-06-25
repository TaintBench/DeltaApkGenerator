import soot.Unit;

public class Choice {
	int ID; 
	Location location;
	
	Choice(int id) {
		this.ID=id;
	}

	public void setChoice(Location location) {
		this.location=location;
	}
	
	public boolean isSameLocation(int lineNo, String jimpleStmt, String preStmt)
	{
		if(location.lineNo!=lineNo)
			return false;
		if(!location.jimpleStmt.equals(jimpleStmt))
			return false;
		if(!location.preStmt.equals(preStmt))
			return false;
		return true;
	}
}
