package com.commandapi.functions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads {@code .json} and {@code .mcfunction} files from the function
 * directory ({@code <config>/commandapi/functions}), mapping nested folders
 * to namespaces: {@code events/mob_attack.json} becomes
 * {@code events:mob_attack}.
 *
 * <p>Rejects path traversal and symlinks escaping the root, rejects duplicate
 * ids, enforces file-size limits, and keeps existing valid functions when one
 * file fails (recording that file's error).</p>
 */
public final class FunctionStorage {
    public static final String FUNCTIONS_DIR = "functions";

    private FunctionStorage() {
    }

    public static Path functionsDir(Path configDir) {
        if (configDir == null) {
            return null;
        }
        return configDir.resolve("commandapi").resolve(FUNCTIONS_DIR);
    }

    /** Creates the directory and loads every function file into the registry. */
    public static LoadResult loadAll(Path configDir, FunctionRegistry registry,
            FunctionLimits limits) throws IOException {
        LoadResult result = new LoadResult();
        if (configDir == null) {
            return result;
        }
        Path root = functionsDir(configDir);
        Files.createDirectories(root);
        List<Path> files = new ArrayList<Path>();
        collect(root, root, files);
        FunctionLimits effective = limits == null ? FunctionLimits.defaults() : limits;
        for (Path file : files) {
            String id = idFor(root, file);
            try {
                long size = Files.size(file);
                if (size > effective.getMaxFileBytes()) {
                    throw new FunctionException("File too large: " + size + " bytes",
                            "FILE_TOO_LARGE");
                }
                byte[] bytes = Files.readAllBytes(file);
                if (bytes.length > effective.getMaxFileBytes()) {
                    throw new FunctionException("File too large", "FILE_TOO_LARGE");
                }
                String text = new String(bytes, StandardCharsets.UTF_8);
                FunctionDefinition definition = FunctionParser.parse(id,
                        file.getFileName().toString(), text, file.toString());
                // The file-system id wins: folders define the namespace.
                if (!definition.getId().equals(id)) {
                    definition = relocate(definition, id);
                }
                FunctionValidator.validate(definition, effective);
                try {
                    registry.register(definition);
                } catch (FunctionException duplicate) {
                    throw new FunctionException("Duplicate function id: " + id,
                            "DUPLICATE_FUNCTION");
                }
                result.loaded.add(id);
            } catch (FunctionException e) {
                result.failures.put(id, e.getMessage());
                registry.recordError(id, e.getMessage());
            }
        }
        return result;
    }

    /** Reloads from disk without dropping existing functions on partial failure. */
    public static LoadResult reload(Path configDir, FunctionRegistry registry,
            FunctionLimits limits) throws IOException {
        FunctionRegistry fresh = new FunctionRegistry();
        LoadResult result = new LoadResult();
        if (configDir == null) {
            return result;
        }
        Path root = functionsDir(configDir);
        Files.createDirectories(root);
        List<Path> files = new ArrayList<Path>();
        collect(root, root, files);
        FunctionLimits effective = limits == null ? FunctionLimits.defaults() : limits;
        for (Path file : files) {
            String id = idFor(root, file);
            try {
                byte[] bytes = Files.readAllBytes(file);
                if (bytes.length > effective.getMaxFileBytes()) {
                    throw new FunctionException("File too large", "FILE_TOO_LARGE");
                }
                FunctionDefinition definition = FunctionParser.parse(id,
                        file.getFileName().toString(),
                        new String(bytes, StandardCharsets.UTF_8), file.toString());
                if (!definition.getId().equals(id)) {
                    definition = relocate(definition, id);
                }
                FunctionValidator.validate(definition, effective);
                try {
                    fresh.register(definition);
                } catch (FunctionException duplicate) {
                    throw new FunctionException("Duplicate function id: " + id,
                            "DUPLICATE_FUNCTION");
                }
                result.loaded.add(id);
            } catch (FunctionException e) {
                result.failures.put(id, e.getMessage());
            }
        }
        // Swap in everything valid; keep previously valid functions that still
        // exist on disk only via the fresh set. Report per-file errors without
        // losing the registry when some files fail: merge failures as error
        // markers while preserving the good entries from both sets.
        synchronized (registry) {
            for (FunctionDefinition definition : fresh.list()) {
                registry.replace(definition);
            }
            for (Map.Entry<String, String> failure : result.failures.entrySet()) {
                registry.recordError(failure.getKey(), failure.getValue());
            }
        }
        return result;
    }

    /** Atomic write of an API-managed definition ({@code <id with _> .json}). */
    public static Path writeJson(Path configDir, String id, String jsonText)
            throws IOException, FunctionException {
        Path root = functionsDir(configDir);
        if (root == null) {
            throw new FunctionException("No config directory", "NO_STORAGE");
        }
        Path target = pathFor(root, id, ".json");
        Files.createDirectories(target.getParent());
        Path tmp = Files.createTempFile(target.getParent(), ".function-", ".tmp");
        try {
            Files.write(tmp, jsonText.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
        return target;
    }

    public static boolean delete(Path configDir, String id) throws IOException,
            FunctionException {
        Path root = functionsDir(configDir);
        if (root == null) {
            throw new FunctionException("No config directory", "NO_STORAGE");
        }
        Path json = pathFor(root, id, ".json");
        Path mc = pathFor(root, id, ".mcfunction");
        boolean removed = Files.deleteIfExists(json) | Files.deleteIfExists(mc);
        return removed;
    }

    static Path pathFor(Path root, String id, String extension) throws FunctionException {
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")) {
            throw new FunctionException("Invalid function id: " + id, "INVALID_ID");
        }
        int colon = id.indexOf(':');
        String namespace = id.substring(0, colon);
        String path = id.substring(colon + 1);
        if (path.contains("..") || namespace.contains("..")) {
            throw new FunctionException("Invalid function id: " + id, "INVALID_ID");
        }
        Path candidate = root.resolve(namespace).resolve(path + extension).normalize();
        if (!candidate.startsWith(root.normalize())) {
            throw new FunctionException("Function id escapes the function directory",
                    "PATH_TRAVERSAL");
        }
        return candidate;
    }

    private static void collect(Path root, Path dir, List<Path> out) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isSymbolicLink(entry)) {
                    Path resolved = entry.toRealPath();
                    if (!resolved.startsWith(root.toRealPath())) {
                        continue; // Symlink escaping the root: skip.
                    }
                    if (Files.isDirectory(resolved)) {
                        collect(root, resolved, out);
                        continue;
                    }
                }
                if (Files.isDirectory(entry)) {
                    collect(root, entry, out);
                } else {
                    String name = entry.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (name.endsWith(".json") || name.endsWith(".mcfunction")) {
                        if (isSafeName(entry.getFileName().toString())) {
                            out.add(entry);
                        }
                    }
                }
            }
        }
    }

    /** Rejects malicious filenames (traversal, hidden, oversized). */
    static boolean isSafeName(String name) {
        if (name == null || name.isEmpty() || name.length() > 128) {
            return false;
        }
        if (name.startsWith(".") || name.contains("..")) {
            return false;
        }
        return name.matches("[A-Za-z0-9_.-]+\\.(json|mcfunction)");
    }

    /** Maps {@code <namespace>/<path>.json} to {@code namespace:path}. */
    static String idFor(Path root, Path file) {
        Path relative = root.relativize(file);
        String text = relative.toString().replace(java.io.File.separatorChar, '/');
        int dot = text.lastIndexOf('.');
        String withoutExt = dot >= 0 ? text.substring(0, dot) : text;
        if (!withoutExt.contains("/")) {
            return "minecraft:" + withoutExt.toLowerCase(Locale.ROOT);
        }
        int slash = withoutExt.indexOf('/');
        String namespace = withoutExt.substring(0, slash).toLowerCase(Locale.ROOT);
        String path = withoutExt.substring(slash + 1).toLowerCase(Locale.ROOT);
        return namespace + ":" + path;
    }

    private static FunctionDefinition relocate(FunctionDefinition definition, String id) {
        return new FunctionDefinition(id, definition.getName(), definition.getDescription(),
                definition.getParameters(), definition.getSteps(), definition.getFormat(),
                definition.getOrigin());
    }

    /** Per-file outcome of a load/reload. */
    public static final class LoadResult {
        public final List<String> loaded = new ArrayList<String>();
        public final Map<String, String> failures = new LinkedHashMap<String, String>();
    }
}
