import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
        return new Grammar(Arrays.stream(str.split("\\R")).filter(Predicate.not(String::isBlank)).map(s -> {
            var parts = s.trim().split("\\s+");
            return new Production(parts[0], Arrays.copyOfRange(parts, 1, parts.length));
        }).toArray(Production[]::new));
    }
}
