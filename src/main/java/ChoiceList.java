import java.util.ArrayList;
import java.util.List;

/** @author Linghui Luo */
public class ChoiceList {
  private List<Choice> choices;

  public ChoiceList() {
    choices = new ArrayList<Choice>();
  }

  public void add(Choice c) {
    this.choices.add(c);
  }

  public boolean isEmpty() {
    return choices.isEmpty();
  }

  public Choice getChoice(int id) {
    for (Choice c : choices) {
      if (c.ID == id) return c;
    }
    return null;
  }

  public int cleanUp() {
    List<Choice> toRemove = new ArrayList<>();
    for (Choice c : choices) {
      if (c.location == null) toRemove.add(c);
    }
    choices.removeAll(toRemove);
    return choices.size();
  }
}
