package analysers;

import org.javatuples.Pair;
import org.javatuples.Triplet;
import parts.Array;
import parts.Class;
import parts.Method;
import parts.PrimitiveType;
import parts.Type;

import java.util.*;
import java.util.stream.Collectors;

public class Parser {

    public static class ParseException extends IllegalArgumentException {

        public ParseException(String message) {
            super(message);
        }
    }

    private final static Map<Character, String> primitive_types;

    static {
        primitive_types = new HashMap<>();
        primitive_types.put('B', "byte");
        primitive_types.put('C', "char");
        primitive_types.put('D', "double");
        primitive_types.put('F', "float");
        primitive_types.put('I', "int");
        primitive_types.put('J', "long");
        primitive_types.put('S', "short");
        primitive_types.put('Z', "boolean");
        primitive_types.put('V', "void");
    }

    public static Pair<List<String>, String> parseDescription(String description) {
        try {
            final PrimitiveIterator.OfInt it = description.chars().iterator();
            char ch = (char) it.nextInt();
            return Parser.parseDescription(ch, it);
        } catch (NoSuchElementException exception) {
            throw new ParseException("Not expected 'eof'");
        }
    }

    private static Pair<List<String>, String> parseDescription(char ch, PrimitiveIterator.OfInt it) {
        final List<String> parameters = new ArrayList<>();
        final StringBuilder type = new StringBuilder();

        if (ch != '(') throw new ParseException(String.format("Expected symbol '(' but not %s", ch));
        StringBuilder token = new StringBuilder();
        int brackets = 0;
        while (true) {
            ch = (char) it.nextInt();
            if (ch == '>') brackets--;
            if (ch == '<') brackets++;
            if (ch == ')') break;
            token.append(ch);
            if ((brackets == 0 && ch == ';') || (token.length() == 1 && primitive_types.containsKey(ch))) {
                if (token.length() == 0) throw new ParseException("Expected 'type name' or '>'");
                parameters.add(token.toString());
                token = new StringBuilder();
            }
        }
        if (brackets > 0) throw new ParseException("Expected '>' before ')'");
        brackets = 0;
        while (it.hasNext()) {
            ch = (char) it.nextInt();
            if (ch == '>') brackets--;
            if (ch == '<') brackets++;
            type.append(ch);
            if (brackets == 0 && ch == ';') break;
        }
        if (brackets > 0) throw new ParseException("Expected '>' before 'eof'");
        if (it.hasNext()) throw new ParseException("Expected 'eof'");
        return new Pair<>(parameters, type.toString());
    }

    public static Triplet<Map<String, String>, List<String>, String> parseSignature(String signature) {
        try {
            final PrimitiveIterator.OfInt it = signature.chars().iterator();
            char ch = (char) it.nextInt();
            return Parser.parseSignature(ch, it);
        } catch (NoSuchElementException exception) {
            throw new ParseException("Not expected 'eof'");
        }
    }

    private static Triplet<Map<String, String>, List<String>, String> parseSignature(char ch, PrimitiveIterator.OfInt it) {
        final Map<String, String> generics = new HashMap<>();
        if (ch == '<') {
            StringBuilder generic = new StringBuilder();
            int brackets = 1;
            while (true) {
                ch = (char) it.nextInt();
                if (ch == '>') brackets--;
                if (ch == '<') brackets++;
                if (brackets == 0) break;
                generic.append(ch);
                if (brackets == 1 && ch == ';') {
                    if (generic.length() == 0) throw new ParseException("Expected 'type name' or '>'");
                    String[] pair = generic.toString().split(":{1,2}");
                    if (pair.length != 2) throw new ParseException(String.format("Expected generic declaration but not %s", generic));
                    generics.put(pair[0], pair[1].replace("/", "."));
                    generic = new StringBuilder();
                }
            }
            if (generic.length() != 0) throw new ParseException("Expected ';' before '>'");
            ch = (char) it.nextInt();
        }
        final Pair<List<String>, String> pair = Parser.parseDescription(ch, it);
        final List<String> parameters = pair.getValue0();
        final String type = pair.getValue1();
        return new Triplet<>(generics, parameters, type);
    }

    public static Type parseType(String name) {
        final char first = name.charAt(0);
        if (primitive_types.containsKey(first)) {
            return new PrimitiveType(name);
        } else if (first == 'L') {
            return Parser.parseClass(name);
        } else if (first == '[') {
            return new Array(Parser.parseType(name.substring(1, name.length())));
        } else {
            throw new ParseException("Expected the target symbol in begin word");
        }
    }

    public static Class parseClass(String full_name) {
        if (!(full_name.length() > 2 && full_name.charAt(0) == 'L' && full_name.charAt(full_name.length() - 1) == ';'))
            throw new ParseException("Expected the symbol 'L' in begin of name and ';' in end");
        full_name = full_name.substring(1, full_name.length() - 1);
        final StringBuilder name = new StringBuilder();
        final StringBuilder generics = new StringBuilder();
        final PrimitiveIterator.OfInt it = full_name.chars().iterator();
        int brackets = 0;
        while (it.hasNext()) {
            char ch = (char) it.nextInt();
            if (ch == '>') {
                brackets--;
                if (brackets == 0) break;
            }
            if (ch == '<') {
                brackets++;
                if (brackets == 0) continue;
            }
            if (brackets == 0) {
                name.append(ch);
            } else {
                generics.append(ch);
            }
        }
        if (brackets > 0) throw new ParseException("Expected '>' before 'eof'");
        if (it.hasNext()) throw new ParseException("Expected 'eof'");
        final List<String> temp = new ArrayList<>(Arrays.asList(name.toString().split("[/.]")));
        final List<String> path = new ArrayList<>(temp.subList(0, temp.size() - 1));
        if (path.size() == 0) throw new ParseException("Invalid full name");
        final List<String> names = new ArrayList<>(Arrays.asList(temp.get(temp.size() - 1).split("\\$")));
        if (names.size() == 0) throw new ParseException("Invalid full name");
        return new Class(path, names);
    }

    public static Method parseMethod(String owner, String name, String desc) {
        return Parser.parseMethod(Parser.parseClass(owner), name, desc);
    }

    public static Method parseMethod(Class owner, String name, String desc) {
        Pair<List<String>, String> doublet = Parser.parseDescription(desc);
        Type type = Parser.parseType(doublet.getValue1());
        List<Type> parameters = doublet.getValue0().stream().map(Parser::parseType).collect(Collectors.toList());
        return new Method(name, owner, type, parameters);
    }
}
