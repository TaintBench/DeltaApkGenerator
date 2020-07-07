/** @author Linghui Luo */
public class MyLogger {

  private boolean print = true;
  private boolean debug = false;

  public MyLogger(boolean print, boolean debug) {
    this.print = print;
    this.debug = debug;
  }

  public void info(String msg) {
    if (print) System.err.println(msg);
  }

  public void debug(String msg) {
    if (debug) System.err.println(msg);
  }
}
