import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.InputMismatchException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import soot.Body;
import soot.BodyTransformer;
import soot.BooleanType;
import soot.ByteType;
import soot.CharType;
import soot.DoubleType;
import soot.FloatType;
import soot.IntType;
import soot.Local;
import soot.LongType;
import soot.PatchingChain;
import soot.PrimType;
import soot.RefLikeType;
import soot.ShortType;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.AbstractStmtSwitch;
import soot.jimple.AssignStmt;
import soot.jimple.DoubleConstant;
import soot.jimple.FloatConstant;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.LongConstant;
import soot.jimple.NullConstant;
import soot.jimple.internal.JSpecialInvokeExpr;

/** @author Linghui Luo */
public class DeltaBodyTransformer extends BodyTransformer {

  private SourceInfo sourceInfo;
  private static int count = 0;
  private boolean inserted;
  protected static boolean canSetFinalTarget = false;
  private List<Unit> targets;
  private HashMap<Unit, Unit> preStmtOfTargets;
  private Map<Unit, String> targetHostClasses;
  private Set<Unit> prevTargets;
  private Unit finalTarget;
  private Choice choice;
  private boolean useLastChoice;

  public DeltaBodyTransformer(SourceInfo sourceInfo, Choice choice) {
    if (choice != null) {
      useLastChoice = true;
      this.choice = choice;
    } else {
      useLastChoice = false;
      this.choice = new Choice(sourceInfo.ID);
    }
    this.targets = new ArrayList<>();
    this.preStmtOfTargets = new HashMap<>();
    this.prevTargets = new HashSet<>();
    this.targetHostClasses = new HashMap<Unit, String>();
    this.sourceInfo = sourceInfo;
    this.inserted = false;
  }

  private Value generateConstant(Type t) {
    if (t instanceof BooleanType) {
      return IntConstant.v(0);
    }
    if (t instanceof ByteType) {
      return IntConstant.v(0);
    }
    if (t instanceof CharType) {
      return IntConstant.v(0);
    }
    if (t instanceof IntType) {
      return IntConstant.v(0);
    }
    if (t instanceof ShortType) {
      return IntConstant.v(0);
    }
    if (t instanceof LongType) {
      return LongConstant.v(0);
    }
    if (t instanceof DoubleType) {
      return DoubleConstant.v(0);
    }
    if (t instanceof FloatType) {
      return FloatConstant.v(0);
    }
    return null;
  }

  /**
   * This method searches possible Jimple statements which match the given sourceInfo. First round:
   * only compare signatures. Second round: if found more than one matches, compare line numbers.
   * Third round: if all matches have different line numbers than sourceInfo, compare arguments.
   *
   * @param method
   * @param units
   */
  private synchronized void searchSourceStmts(SootMethod method, PatchingChain units) {
    if (!sourceInfo.isAnonymousClass) canSetFinalTarget = true;
    Unit preTarget = null;
    for (Iterator iter = units.snapshotIterator(); iter.hasNext(); ) {
      Unit unit = (Unit) iter.next();
      if (sourceInfo.match(method, unit, false, false)) {
        targets.add(unit);
        preStmtOfTargets.put(unit, preTarget);
        targetHostClasses.put(unit, method.getDeclaringClass().getName());
      }
      preTarget = unit;
    }
    if (canSetFinalTarget) {
      List<Unit> removed = new ArrayList<>();
      if (targets.size() > 1) {
        // if there are more than 1 units match the source, compare the line numbers
        for (Unit target : targets) {
          if (!sourceInfo.match(method, target, true, false)) {
            removed.add(target);
          }
        }
        // if all units have different line numbers than the searched one, compare
        // arguments
        if (removed.size() == targets.size()) {
          removed.clear();
          for (Unit target : targets) {
            if (!sourceInfo.match(method, target, true, true)) removed.add(target);
          }
        }
      }
      if (targets.size() > 0) {
        if (removed.size() < targets.size()) {
          targets.removeAll(removed);
          removed.clear();
        }
      }
    }
  }

  private synchronized void setFinalTarget() {

    if (targets.size() > 1) {
      if (!inserted)
        if (prevTargets.size() != targets.size()) {
          StringBuilder str =
              new StringBuilder(
                  "Multiple possible matches were found. Please choose one to kill by giving the number:\n");
          int i = 0;

          boolean chosen = false;
          for (Unit target : targets) {
            i++;
            str.append(
                "\t"
                    + i
                    + ": "
                    + target.toString()
                    + " @lineNo."
                    + target.getJavaSourceStartLineNumber()
                    + " @class "
                    + targetHostClasses.get(target)
                    + "\n");

            Unit pre = preStmtOfTargets.get(target);
            str.append("\t after " + pre.toString() + "\n");
            if (choice != null && useLastChoice) {
              String preStmt = null;
              if (pre != null) preStmt = pre.toString();
              if (choice.isSameLocation(
                  target.getJavaSourceStartLineNumber(), target.toString(), preStmt)) {
                finalTarget = target;
                chosen = true;
                break;
              }
            }
          }
          if (useLastChoice && !chosen) {
            Main.LOGGER.info(
                "Couldn't pick a choice automatically for flow with id " + sourceInfo.ID);
          }
          if (!chosen) {
            str.append("Your choice (enter a number, enter 0 if none of them should be chosen): ");
            Main.LOGGER.info(str.toString());
            Scanner sc = new Scanner(System.in);
            try {
              int choice = sc.nextInt();
              if (choice == 0) {
                Main.LOGGER.info("You have chosen none of the above matches");
              } else if (choice < 0 || choice > targets.size()) {
                Main.LOGGER.info("Please input a number between 0 and " + targets.size());
              } else {
                finalTarget = targets.get(choice - 1);
                Main.LOGGER.info(
                    "You have chosen "
                        + choice
                        + ": "
                        + finalTarget.toString()
                        + " @lineNo."
                        + finalTarget.getJavaSourceStartLineNumber());
                if (!useLastChoice) {
                  Unit pres = preStmtOfTargets.get(finalTarget);
                  String preStmt = null;
                  if (pres != null) preStmt = pres.toString();
                  Location location =
                      new Location(
                          finalTarget.getJavaSourceStartLineNumber(),
                          finalTarget.toString(),
                          preStmt);
                  this.choice.setChoice(location);
                }
              }
            } catch (InputMismatchException e) {
              throw new RuntimeException("Please input an integer number");
            }
          }

          this.prevTargets.addAll(targets);
        }
    } else if (targets.size() == 1 && finalTarget == null) {
      if (targets.get(0).getJavaSourceStartLineNumber() == sourceInfo.lineNo)
        canSetFinalTarget = true;
      if (canSetFinalTarget) finalTarget = targets.get(0);
    }
  }

  @Override
  protected void internalTransform(Body b, String phaseName, Map<String, String> options) {
    if (!inserted) {
      SootMethod method = b.getMethod();
      PatchingChain units = b.getUnits();
      searchSourceStmts(method, units);
      setFinalTarget();
      if (finalTarget != null)
        for (Iterator iter = units.snapshotIterator(); iter.hasNext(); ) {
          Unit unit = (Unit) iter.next();
          unit.apply(
              new AbstractStmtSwitch() {
                public synchronized void insert(Unit toInsert, Unit stmt) {
                  if (!inserted) {
                    units.insertAfter(toInsert, stmt);
                    b.validate();
                    count++;
                    inserted = true;
                    Main.LOGGER.info(
                        count
                            + ". "
                            + "Killed a flow from "
                            + stmt
                            + " @lineNo."
                            + stmt.getJavaSourceStartLineNumber()
                            + "\nby inserting "
                            + toInsert.toString()
                            + " after it.");
                  }
                }

                public void caseAssignStmt(AssignStmt stmt) {
                  if (finalTarget.equals(stmt)) {
                    Value left = stmt.getLeftOp();
                    Value right = null;
                    if (left instanceof Local) {
                      Local local = (Local) left;
                      Type t = local.getType();
                      if (t instanceof RefLikeType) {
                        right = NullConstant.v();
                      } else if (t instanceof PrimType) {
                        right = generateConstant(t);
                      }
                    }
                    if (right != null) {
                      Unit toInsert = Jimple.v().newAssignStmt(left, right);
                      insert(toInsert, stmt);
                    }
                  }
                }

                public void caseInvokeStmt(InvokeStmt stmt) {
                  if (finalTarget.equals(stmt)) {
                    InvokeExpr expr = stmt.getInvokeExpr();
                    // this is for the case that the source API is a constructor
                    if (expr instanceof JSpecialInvokeExpr) {
                      JSpecialInvokeExpr je = (JSpecialInvokeExpr) expr;
                      Value left = je.getBase();
                      Value right = NullConstant.v();
                      Unit toInsert = Jimple.v().newAssignStmt(left, right);
                      insert(toInsert, stmt);
                    }
                  }
                }
              });
        }
    }
  }

  public Choice getChoice() {
    return this.choice;
  }
}
