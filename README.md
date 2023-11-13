# Simple Earley Parser

This is a walkthrough of how to create a simple Earley parser in Java to decide if a word is in the language defined by a context-free grammar. Basic knowledge of context-free grammars is assumed.

Let's start off with the representation of a production/rule:

```java
public record Production(String lhs, String[] rhs) {
}
```

The `lhs` symbol directly derives the sequence of symbols `rhs`.

Next, we define a `Grammar` class. It is not too important to understand this code, as it is provided for convenience.

```java
public class Grammar {
    final Set<String> nonTerminals;
    final Set<String> terminals;
    final String start;
    final Map<String, List<Production>> productionsExpanding;

    public Grammar(final Production[] productions) {
        if (Objects.requireNonNull(productions, "productions must not be null").length == 0)
            throw new IllegalArgumentException("productions must not be empty");
        this.nonTerminals = Arrays.stream(productions).map(Production::lhs).collect(Collectors.toUnmodifiableSet());
        this.terminals = Arrays.stream(productions).flatMap(prod -> Arrays.stream(prod.rhs()))
                .filter(Predicate.not(nonTerminals::contains)).collect(Collectors.toUnmodifiableSet());
        this.start = productions[0].lhs();
        this.productionsExpanding = Arrays.stream(productions).collect(Collectors.collectingAndThen(
                Collectors.groupingBy(Production::lhs, Collectors.toUnmodifiableList()), Collections::unmodifiableMap));
    }

    public static Grammar parse(String str) {
        return new Grammar(str.lines().filter(Predicate.not(String::isBlank)).map(s -> {
            var parts = s.trim().split("\\s+");
            return new Production(parts[0], Arrays.copyOfRange(parts, 1, parts.length));
        }).toArray(Production[]::new));
    }
}
```

Now, we can start constructing our Earley parser based on a given grammar.

```java
public class EarleyParser {
    private final Grammar grammar;

    public EarleyParser(final Grammar grammar) {
        this.grammar = grammar;
    }
    
    public boolean wordInGrammar(final String[] word) {
        // ???
    }
}
```

The rest of the implementation will be inside the `wordInGrammar` method.

We define a `State` record class to represent a certain step in the parsing.

```java
record State(String name, String[] parts, int pos, int startIdx) {
}
```

`name` is the nonterminal symbol that we are attempting to match against the input word using the production whose `rhs` is equal to `parts`.

`pos` is the next index to consider in the parsing of this production.

`startIdx` is the index at which we started considering this rule.

We define that a `State` is done when we have reached the end of the symbols (`parts`) and completely matched the production. We add a method to `State` that reports this status.

```java
public boolean isDone() {
    return pos >= parts.length;
}
```

We also add another method to create a new `State` with the position advanced by one (after a match is found).

```java
State advance() {
    return new State(name, parts, pos + 1, startIdx);
}
```

Then, we set up a `Set` of `State`s for every index in the input word, plus one more for the states after matching the final symbol in the input. As Earley is a chart parser, we shall name this array `chart`. It will be used for dynamic programming.

```java
final Set<State>[] chart = new Set[word.length + 1];
Arrays.setAll(chart, i -> new HashSet<>()); // replace initial null values
```

We use a `Queue` to handle all the new states at each index in the word, one by one.

```java
final Queue<State> queue = new ArrayDeque<>();
```

The initial states are formed from all the productions based on the grammar's start symbol (it is assumed to be the `lhs` of the first `Production` passed to the constructor of the `Grammar` class). This is the beginning of the parsing, so the `pos` for each `State` is `0`, as is the `startIdx`. Each `State` is added to the `Set` for the first index in the `chart` before adding to the `Queue` to avoid duplicates.

```java
for (var prod : grammar.productionsExpanding.get(grammar.start)) {
    var state = new State(prod.lhs(), prod.rhs(), 0, 0);
    if (chart[0].add(state))
        queue.add(state);
}
```

There is often only one production for the start, so the loop may not be necessary.

The main part of the algorithm starts with a loop over each index in the `chart`.

```java
for (int i = 0; i < chart.length; i++) {
    final List<State> nextElems = new ArrayList<>(); // stores states for the next iteration
    while (!queue.isEmpty()) { // go through every state in the queue until there's nothing left
        // ...
    }
}
```

Let's dive into the `while` loop. The first step is to retrieve and remove the head of the queue.

```java
final var currState = queue.poll();
```

There are now 3 cases to handle. If this state is done, we can advance all the states that started at the same index as it and were waiting for that specific symbol (the `name` of this `State`) to continue, i.e. the symbol at the current `pos` for that state is the `name` of the current state. The code that handles this is often referred to as the completer.

```java
if (currState.isDone()) {
    var parStates = chart[currState.startIdx].stream()
            .filter(prev -> !prev.isDone() && prev.parts[prev.pos].equals(currState.name)).toList();
    for (var par : parStates) {
        var nextState = par.advance();
        if (chart[i].add(nextState))
            queue.add(nextState);
    }
}
```

For each of these advanced parent states, we check if it already existed in set of states for the current index by attempting to add it to this set. `add` returns `true` iff the element was added, which also means that element did not already exist in the `Set`. The state is only added to the queue if it is new. This avoids reprocessing the same states over and over.

If the current state is not complete, we check if the symbol at the current `pos` in the state is non-terminal. If it is, we can try all the productions for it, adding new states for each production to the set for the current index. Each of these states has the `pos` set to `0` (as it's a new production that's just being started) and the `startIdx` is the current index (as we started considering the specific production at this point). This step is commonly called the predictor.

```java
if (currState.isDone()) {
    // completer code..
} else {
    final String symbol = currState.parts[currState.pos];
    if (grammar.nonTerminals.contains(symbol)) {
        for (var prod : grammar.productionsExpanding.get(symbol)) {
            var nextState = new State(prod.lhs(), prod.rhs(), 0, i);
            if (chart[i].add(nextState)) // prevent duplicates
                queue.add(nextState);
        }
    }
}
```

Otherwise, if the symbol at the current `pos` is terminal, then there is only way for it to match: if the symbol at the same index in the input word is exactly equal to the current symbol. We additionally check that the index is in range first since the last iteration will have the index be one past the end of the word.

If the symbols match, we can advance the current state and add it to the states for the next index, as we have consumed one symbol in the word. This stage is known as the scanner.

```java
final String symbol = currState.parts[currState.pos];
if (grammar.nonTerminals.contains(symbol)) {
    // predict step...
} else if (i < word.length && word[i].equals(symbol)) {
    // scan step
    var nextState = currState.advance();
    if (chart[i + 1].add(nextState))
        nextElems.add(nextState);
}
```

After the `while` loop that handles all the states for the current index, we fill the `queue` with the states for the next index for the subsequent iteration.

```java
queue.addAll(nextElems);
```

Finally, the answers await us in the last element of the `chart` after the `for` loop finishes executing.
To determine if the word is in the grammar, we check if the last `Set` of `State`s contains a done start `State`.

```java
return chart[chart.length - 1].stream().anyMatch(s -> s.isDone() && s.name.equals(grammar.start));
```

The full code for the method is as shown below:

```java
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
```

Let's try it!

```java
public class Main {
    public static void main(String[] args) {
        var grammar = Grammar.parse("""
S BOF defdefs EOF
defdefs defdef defdefs
defdefs defdef
defdef DEF ID LPAREN parmsopt RPAREN COLON type BECOMES LBRACE vardefsopt defdefsopt expras RBRACE
parmsopt parms
parmsopt
parms vardef COMMA parms
parms vardef
vardef ID COLON type
type INT
type LPAREN typesopt RPAREN ARROW type
typesopt types
typesopt
types type COMMA types
types type
vardefsopt VAR vardef SEMI vardefsopt
vardefsopt
defdefsopt defdefs
defdefsopt
expras expra SEMI expras
expras expra
expra ID BECOMES expr
expra expr
expr IF LPAREN test RPAREN LBRACE expras RBRACE ELSE LBRACE expras RBRACE
expr term
expr expr PLUS term
expr expr MINUS term
term factor
term term STAR factor
term term SLASH factor
term term PCT factor
factor ID
factor NUM
factor LPAREN expr RPAREN
factor factor LPAREN argsopt RPAREN
test expr NE expr
test expr LT expr
test expr LE expr
test expr GE expr
test expr GT expr
test expr EQ expr
argsopt args
argsopt
args expr COMMA args
args expr""");
        var parser = new EarleyParser(grammar);
        System.out.println(parser.wordInGrammar(new String[] {
            "BOF", "DEF", "ID", "LPAREN", "RPAREN", "COLON", "INT", "BECOMES", "LBRACE", "NUM", "RBRACE", "EOF"
        })); // true
    }
}
```

Implementing this parsing algorithm in Scala (and golfing it, of course) is left as an exercise to the reader :)
