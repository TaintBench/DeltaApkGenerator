import java.util.regex.Matcher;
import java.util.regex.Pattern;

import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.InvokeStmt;

public class SourceInfo {

  protected String statement;
  protected String methodName;
  protected String className;
  protected int lineNo;
  protected String targetName;
  protected int targetNo;
  protected int ID;

  protected Pattern constructorPattern;
  protected Pattern normalInvokePattern;

  protected boolean isAnonymousClass;

  public SourceInfo(
      String statement,
      String methodName,
      String className,
      int lineNo,
      String targetName,
      int targetNo,
      int ID) {
    this.statement = statement;
    this.methodName = methodName;
    this.className = className;
    this.lineNo = lineNo;
    this.targetName = targetName;
    this.targetNo = targetNo;
    this.ID = ID;
    String constructorRegex =
        "specialinvoke.+<.*" + targetName + ":\\s" + "void" + "\\s" + "<init>\\(.+\\)";
    String normalInvokeRegex = ".+invoke.+<.+" + "\\s+" + targetName + "\\(.*\\)>";
    constructorPattern = Pattern.compile(constructorRegex);
    normalInvokePattern = Pattern.compile(normalInvokeRegex);
  }

  private boolean isTargetConstructor() {
    String regex = "new\\s+" + targetName + "\\s*\\(";
    Pattern p = Pattern.compile(regex);
    Matcher matcher = p.matcher(statement);
    return matcher.find();
  }

  private boolean searchConditionSatisfied(Unit unit) {
    String unitStr = unit.toString();
    if (isTargetConstructor()) {
      if (unit instanceof InvokeStmt)
        if (unitStr.contains("init")) {
          Matcher matcher = constructorPattern.matcher(unitStr);
          return matcher.find();
        }
      return false;
    } else {
      if (unitStr.contains(targetName)) {
        Matcher matcher = normalInvokePattern.matcher(unitStr);
        return matcher.find();
      } else return false;
    }
  }

  private boolean isSameClass(boolean isAnonymousClass, String cName) {
    if (isAnonymousClass) return cName.startsWith(getHostClassName());
    else return className.equals(cName);
  }

  private boolean isAnonymousClass() {
    this.isAnonymousClass = this.className.contains(".AnonymousClass");
    return this.isAnonymousClass;
  }

  private String getHostClassName() {
    String[] names = this.className.split(".AnonymousClass");
    if (names.length > 0) return names[0];
    else return className;
  }

  public boolean match(SootMethod method, Unit unit, boolean compaireLineNo, boolean compairArgs) {
    String cName = method.getDeclaringClass().getName();
    String mName = method.getName();
    boolean match = false;
    if (cName.contains("$") && method.getDeclaringClass().hasOuterClass()) { // handle inner classes
      String outerName = method.getDeclaringClass().getOuterClass().getName();
      String innerName = cName.substring(outerName.length() + 1, cName.length());
      cName = outerName + "." + innerName;
    }
    boolean classFound = isSameClass(isAnonymousClass(), cName);
    if (classFound && methodName.contains(mName)) {
      if (searchConditionSatisfied(unit)) {
        if (compaireLineNo) {
          if (unit.getJavaSourceStartLineNumber() == lineNo) {
            match = true;
          } else {
            if (compairArgs) {
              // sometimes the line numbers are not the same, but actually mean the same line
              // of code, we need to check the arguments.
              if (unit instanceof AssignStmt) {
                boolean allArgsMatched = true;
                AssignStmt assignStmt = (AssignStmt) unit;
                if (assignStmt.containsInvokeExpr()) {
                  for (Value arg : assignStmt.getInvokeExpr().getArgs()) {
                    if (!statement.contains(arg.toString())) {
                      allArgsMatched = false;
                    }
                  }
                  match = allArgsMatched;
                }
              }
            }
          }
        } else {
          match = true;
        }
      }
    }
    if (match) {
      Main.LOGGER.debug(
          "Found a possible match!\nJimple:\n\thost: "
              + method.toString()
              + "\n\tstmt: "
              + unit.toString()
              + "\n\tlineNo: "
              + unit.getJavaSourceStartLineNumber()
              + "\n");
    }
    return match;
  }

  @Override
  public String toString() {
    StringBuilder str = new StringBuilder();
    str.append(
        ID
            + "\n\thost: "
            + className
            + ": "
            + methodName
            + "\n\tstmt: "
            + statement
            + "\n\tlineNo: "
            + lineNo
            + "\n\ttargetName: "
            + targetName
            + "\n\ttargetNo: "
            + targetNo
            + "\n");
    return str.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ID;
    result = prime * result + ((className == null) ? 0 : className.hashCode());
    result = prime * result + lineNo;
    result = prime * result + ((methodName == null) ? 0 : methodName.hashCode());
    result = prime * result + ((statement == null) ? 0 : statement.hashCode());
    result = prime * result + ((targetName == null) ? 0 : targetName.hashCode());
    result = prime * result + targetNo;
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    SourceInfo other = (SourceInfo) obj;
    if (ID != other.ID) return false;
    if (className == null) {
      if (other.className != null) return false;
    } else if (!className.equals(other.className)) return false;
    if (lineNo != other.lineNo) return false;
    if (methodName == null) {
      if (other.methodName != null) return false;
    } else if (!methodName.equals(other.methodName)) return false;
    if (statement == null) {
      if (other.statement != null) return false;
    } else if (!statement.equals(other.statement)) return false;
    if (targetName == null) {
      if (other.targetName != null) return false;
    } else if (!targetName.equals(other.targetName)) return false;
    if (targetNo != other.targetNo) return false;
    return true;
  }
}
