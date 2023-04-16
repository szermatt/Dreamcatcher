package net.gmx.szermatt.dreamcatcher.harmony;

import static java.lang.String.format;

import org.jivesoftware.smack.packet.IQ;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract class OAReplyParser {
    /*
     * FIXME: This parser could be far cleaner than it is, given the possibility of the pseudo-json components
     * containing colons, and the structure of them
     */
    static final Pattern kvRE = Pattern.compile("(.*?)=(.*)");
    static Set<String> validResponses = new HashSet<>();

    static {
        validResponses.add("100");
        validResponses.add("200");
        validResponses.add("506"); // Bluetooth not connected
        validResponses.add("566"); // Command not found for device, recoverable
    }

    protected static Map<String, Object> parseKeyValuePairs(String statusCode, String errorString, String contents) {
        Map<String, Object> params = new HashMap<>();
        if (statusCode != null) {
            params.put("statusCode", statusCode);
        }
        if (errorString != null) {
            params.put("errorString", errorString);
        }
        for (String pair : contents.split(":")) {
            Matcher matcher = kvRE.matcher(pair);
            if (!matcher.matches()) {
                continue;
                // throw new AuthFailedException(format("failed to parse element in auth response: %s", pair));
            }
            Object valueObj;
            String value = matcher.group(2);
            if (value.startsWith("{")) {
                valueObj = parsePseudoJson(value);
            } else {
                valueObj = value;
            }
            params.put(matcher.group(1), valueObj);
        }

        return params;
    }

    protected static Map<String, Object> parsePseudoJson(String value) {
        Map<String, Object> params = new HashMap<>();
        value = value.substring(1, value.length() - 1);
        for (String pair : value.split(", ?")) {
            Matcher matcher = kvRE.matcher(pair);
            if (!matcher.matches()) {
                throw new AuthFailedException(format("failed to parse element in auth response: %s", value));
            }
            params.put(matcher.group(1), parsePseudoJsonValue(matcher.group(2)));
        }
        return params;
    }

    private static Object parsePseudoJsonValue(String value) {
        switch (value.charAt(0)) {
            case '{':
                return parsePseudoJsonValue(value);
            case '"':
                return value.substring(1, value.length() - 1);
            case '\'':
                return value.substring(1, value.length() - 1);
            default:
                try {
                    return Long.parseLong(value);
                } catch (NumberFormatException e) {
                    // do nothing
                }
                try {
                    return Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    // do nothing
                }
                return value;
        }
    }

    public abstract IQ parseReplyContents(String statusCode, String errorString, String contents);

    public boolean validResponseCode(String code) {
        return validResponses.contains(code);
    }
}
