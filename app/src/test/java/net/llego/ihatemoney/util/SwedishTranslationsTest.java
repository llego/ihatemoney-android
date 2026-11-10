package net.llego.ihatemoney.util;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SwedishTranslationsTest {
    @Test
    public void coversAllAppStringsAndPreservesFormatArguments() throws Exception {
        File resources = new File("src/main/res");
        if (!resources.isDirectory()) resources = new File("app/src/main/res");
        Map<String, Element> defaults = read(new File(resources, "values"));
        Map<String, Element> swedish = read(new File(resources, "values-sv-rSE"));
        for (Map.Entry<String, Element> entry : defaults.entrySet()) {
            Element source = entry.getValue();
            Element translated = swedish.get(entry.getKey());
            if (source.getAttribute("translatable").equals("false")) {
                if (translated != null) {
                    assertEquals(entry.getKey(), source.getTextContent(), translated.getTextContent());
                }
                continue;
            }
            assertNotNull("Missing Swedish translation: " + entry.getKey(), translated);
            if (source.getTagName().equals("string")) {
                assertEquals(entry.getKey(), arguments(source.getTextContent()),
                        arguments(translated.getTextContent()));
            } else {
                List<Element> sourceItems = items(source);
                List<Element> translatedItems = items(translated);
                assertEquals(entry.getKey(), sourceItems.size(), translatedItems.size());
                for (int i = 0; i < sourceItems.size(); i++) {
                    assertEquals(entry.getKey(), sourceItems.get(i).getAttribute("quantity"),
                            translatedItems.get(i).getAttribute("quantity"));
                    assertEquals(entry.getKey(), arguments(sourceItems.get(i).getTextContent()),
                            arguments(translatedItems.get(i).getTextContent()));
                }
            }
        }
        for (String key : swedish.keySet()) {
            assertTrue("Unknown Swedish resource: " + key, defaults.containsKey(key));
        }
    }

    private static Map<String, Element> read(File directory) throws Exception {
        Map<String, Element> result = new HashMap<>();
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".xml"));
        assertNotNull("Missing resources: " + directory, files);
        for (File file : files) {
            Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(file).getDocumentElement();
            for (Element element : items(root)) {
                String type = element.getTagName();
                if (type.equals("string") || type.equals("plurals") || type.equals("string-array")) {
                    String key = type + "/" + element.getAttribute("name");
                    assertTrue("Duplicate resource: " + key, !result.containsKey(key));
                    result.put(key, element);
                }
            }
        }
        return result;
    }

    private static List<Element> items(Element parent) {
        List<Element> result = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element) result.add((Element) child);
        }
        return result;
    }

    private static List<String> arguments(String text) {
        Matcher matcher = Pattern.compile("%(?:\\d+\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?[a-zA-Z%]")
                .matcher(text);
        List<String> result = new ArrayList<>();
        while (matcher.find()) result.add(matcher.group());
        Collections.sort(result);
        return result;
    }
}
