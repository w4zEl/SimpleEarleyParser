import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class EarleyParser {
    private final Grammar grammar;

    public EarleyParser(final Grammar grammar) {
        this.grammar = grammar;
    }

    public boolean wordInGrammar(final String[] word) {
        record State(String name, String[] parts, int pos, int startIdx) {
            public boolean isDone() {
                return pos >= parts.length;
            }

            State advance() {
                return new State(name, parts, pos + 1, startIdx);
            }
        }
        
        final Set<State>[] chart = new Set[word.length + 1];
        Arrays.setAll(chart, i -> new HashSet<>());
        final Queue<State> queue = new ArrayDeque<>();

        // set up initial states (handles multiple production rules for start symbol)
        for (var prod : grammar.productionsExpanding.get(grammar.start)) {
            var state = new State(prod.lhs(), prod.rhs(), 0, 0);
            if (chart[0].add(state))
                queue.add(state);
        }

        for (int i = 0; i < chart.length; i++) {
            final List<State> nextElems = new ArrayList<>();
            while (!queue.isEmpty()) {
                final var currState = queue.poll();
                if (currState.isDone()) {
                    // complete step
                    var parStates = chart[currState.startIdx].stream()
                            .filter(prev -> !prev.isDone() && prev.parts[prev.pos].equals(currState.name)).toList();
                    for (var par : parStates) {
                        var nextState = par.advance();
                        if (chart[i].add(nextState))
                            queue.add(nextState);
                    }
                } else {
                    final String symbol = currState.parts[currState.pos];
                    if (grammar.nonTerminals.contains(symbol)) {
                        // predict step
                        for (var prod : grammar.productionsExpanding.get(symbol)) {
                            var nextState = new State(prod.lhs(), prod.rhs(), 0, i);
                            if (chart[i].add(nextState))
                                queue.add(nextState);
                        }
                    } else if (i < word.length && word[i].equals(symbol)) {
                        // scan step
                        var nextState = currState.advance();
                        if (chart[i + 1].add(nextState))
                            nextElems.add(nextState);
                    }
                }
            }
            queue.addAll(nextElems);
        }

        return chart[chart.length - 1].stream().anyMatch(s -> s.isDone() && s.name.equals(grammar.start));
    }
}
