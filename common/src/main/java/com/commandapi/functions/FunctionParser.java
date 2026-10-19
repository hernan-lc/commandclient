package com.commandapi.functions;

/** Dispatches raw file text to the JSON or .mcfunction parser by filename. */
public final class FunctionParser {
    private FunctionParser() {
    }

    public static FunctionDefinition parse(String functionId, String fileName, String text,
            String origin) throws FunctionException {
        if (fileName != null && fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".json")) {
            return JsonFunctionParser.parse(functionId, text, origin);
        }
        if (fileName != null && fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".mcfunction")) {
            return McFunctionParser.parse(functionId, text, origin);
        }
        // Content sniffing for API-submitted bodies without a filename.
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("{")) {
            return JsonFunctionParser.parse(functionId, text, origin);
        }
        return McFunctionParser.parse(functionId, text, origin);
    }
}
