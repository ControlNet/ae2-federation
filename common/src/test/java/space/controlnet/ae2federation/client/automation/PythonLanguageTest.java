package space.controlnet.ae2federation.client.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.lowdragmc.lowdraglib2.gui.ui.elements.codeeditor.language.SyntaxParser;
import com.lowdragmc.lowdraglib2.gui.ui.elements.codeeditor.language.Token;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The script editor's colours: every character of a line lands in a token, each of the kind the design colours. */
final class PythonLanguageTest {
    private static List<String> kinds(String line) {
        var parser = new SyntaxParser();
        parser.setLanguageDefinition(PythonLanguage.INSTANCE);
        var tokens = parser.parseLine(line);
        assertEquals(line, String.join("", tokens.stream().map(Token::text).toList()), "every character is drawn");
        return tokens.stream().filter(token -> !token.type().name.equals("space"))
                .map(token -> token.type().name + ":" + token.text()).toList();
    }

    @Test
    void aDefinitionColoursItsNameParametersAndTypes() {
        assertEquals(List.of("keyword:def", "definition:refill", "operator:(", "name:name", "operator::", "type:Item",
                "operator:,", "name:lamp", "operator::", "type:Interface", "operator:)", "operator:->", "type:None",
                "operator::"), kinds("def refill(name: Item, lamp: Interface) -> None:"));
    }

    @Test
    void namespacesCallsStringsNumbersAndComments() {
        assertEquals(List.of("namespace:ae2", "operator:.", "name:craft", "operator:(", "call:_low",
                "operator:(", "string:\"minecraft:iron_ingot\"", "operator:)", "operator:,", "number:64",
                "operator:-", "number:1.5", "operator:)", "comment:# the rest"),
                kinds("ae2.craft(_low(\"minecraft:iron_ingot\"), 64-1.5) # the rest"));
    }

    @Test
    void anUnclosedStringAndOddCharactersStillDraw() {
        assertEquals(List.of("name:x", "operator:=", "string:'abc"), kinds("x = 'abc"));
        assertEquals(List.of("keyword:if", "constant:True", "operator::", "name:é", "operator:$"),
                kinds("if True: é $"));
    }

    /** Typing "1" then "a" left "1a", which no kind matched: the editor drew only the "a". */
    @Test
    void aNumberRunIntoLettersStillDraws() {
        assertEquals(List.of("number:1", "name:a"), kinds("1a"));
        assertEquals(List.of("name:x", "operator:=", "number:12", "name:ab", "operator:+", "operator:²"),
                kinds("x = 12ab + ²"));
    }

    /** The guide colours a listing with the editor's colours, kind by kind. */
    @Test
    void eachKindHasTheEditorsColour() {
        assertEquals(0xb37bea, PythonLanguage.color("keyword"));
        assertEquals(0xf7b13a, PythonLanguage.color("string"));
        assertEquals(PythonLanguage.NAMESPACE, PythonLanguage.color("namespace"));
        assertEquals(0xd6d0e0, PythonLanguage.color("space"));
    }
}
