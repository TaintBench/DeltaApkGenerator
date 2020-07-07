/** @author Linghui Luo */
public class Location {
  int lineNo;
  String jimpleStmt;
  String preStmt;

  public Location(int lineNo, String jimpleStmt, String preStmt) {
    this.lineNo = lineNo;
    this.jimpleStmt = jimpleStmt;
    this.preStmt = preStmt;
  }
}
