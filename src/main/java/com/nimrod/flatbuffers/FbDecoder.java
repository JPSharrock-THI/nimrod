package com.nimrod.flatbuffers;

import com.google.flatbuffers.Table;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decodes FlatBuffer binary blobs into {@code Map<String, Object>} suitable for JSON serialisation.
 *
 * <p>Uses the compiled FBS Java classes from sup-server-db-fbs-schema. Auto-detects the
 * schema via file_identifier, deserialises the root table, then reflectively walks all
 * getter methods to produce a generic map representation.</p>
 */
@Component
public class FbDecoder {

    private static final Logger LOG = LoggerFactory.getLogger(FbDecoder.class);

    /** Method names inherited from Table/Object that should be skipped during reflection. */
    private static final Set<String> SKIP_METHODS = Set.of(
            "getClass", "hashCode", "toString", "notify", "notifyAll", "wait",
            "getByteBuffer", "equals"
    );

    /** Suffixes for internal FlatBuffer accessor methods that we handle separately or skip. */
    private static final Set<String> SKIP_SUFFIXES = Set.of(
            "Vector", "AsByteBuffer", "InByteBuffer", "AsTable"
    );

    private final SchemaRegistry schemaRegistry;

    public FbDecoder(SchemaRegistry schemaRegistry) {
        this.schemaRegistry = schemaRegistry;
    }

    /**
     * Decode a FlatBuffer blob into a map.
     *
     * @param buffer the raw FlatBuffer bytes (must include the 4-byte file_identifier at offset 4)
     * @return a map of field names to decoded values
     * @throws IllegalArgumentException if no matching schema is found
     */
    public Map<String, Object> decode(ByteBuffer buffer) {
        SchemaRegistry.SchemaEntry entry = schemaRegistry.findByBuffer(buffer)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No matching FBS schema for buffer. Known schemas:\n"
                                + schemaRegistry.formatSchemaList()));

        LOG.debug("Matched schema: {}", entry.simpleName());
        Table root = entry.deserialize(buffer);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("_type", entry.simpleName());
        result.putAll(tableToMap(root));
        return result;
    }

    /**
     * Reflectively walk a FlatBuffer Table object and extract all fields into a map.
     *
     * <p>FlatBuffer generated Java classes expose fields as methods:
     * <ul>
     *   <li>{@code fieldName()} — scalar or nested table getter (0 args)</li>
     *   <li>{@code fieldNameLength()} — vector length (0 args, returns int)</li>
     *   <li>{@code fieldName(int)} — vector element access (1 int arg)</li>
     *   <li>{@code fieldNameType()} — union discriminator (returns byte)</li>
     * </ul>
     */
    private Map<String, Object> tableToMap(Object obj) {
        if (obj == null) {
            return null;
        }

        Class<?> clazz = obj.getClass();
        Map<String, Object> result = new LinkedHashMap<>();

        // Categorise methods
        Map<String, Method> noArgMethods = new LinkedHashMap<>();
        Map<String, Method> intArgMethods = new LinkedHashMap<>();
        Map<String, Method> lengthMethods = new LinkedHashMap<>();

        for (Method m : clazz.getMethods()) {
            String name = m.getName();

            if (shouldSkipMethod(m, name)) {
                continue;
            }

            if (m.getParameterCount() == 0 && m.getReturnType() != void.class) {
                if (name.endsWith("Length")) {
                    String baseName = name.substring(0, name.length() - 6);
                    lengthMethods.put(baseName, m);
                } else {
                    noArgMethods.put(name, m);
                }
            }

            if (m.getParameterCount() == 1
                    && m.getParameterTypes()[0] == int.class
                    && m.getReturnType() != void.class) {
                intArgMethods.put(name, m);
            }
        }

        // Process vector fields first (those with *Length methods)
        for (var entry : lengthMethods.entrySet()) {
            String baseName = entry.getKey();
            Method lengthMethod = entry.getValue();
            Method elementMethod = intArgMethods.get(baseName);

            if (elementMethod != null) {
                try {
                    int length = (int) lengthMethod.invoke(obj);
                    List<Object> list = new ArrayList<>(length);
                    for (int i = 0; i < length; i++) {
                        Object elem = elementMethod.invoke(obj, i);
                        list.add(convertValue(elem));
                    }
                    result.put(baseName, list);
                    // Remove from noArgMethods so we don't double-process
                    noArgMethods.remove(baseName);
                } catch (Exception e) {
                    LOG.warn("Failed to read vector field '{}' on {}: {}",
                            baseName, clazz.getSimpleName(), e.getMessage());
                }
            }
        }

        // Process remaining no-arg methods (scalar and nested table fields)
        for (var entry : noArgMethods.entrySet()) {
            String name = entry.getKey();
            Method method = entry.getValue();

            // Skip vector-related methods — already handled above
            if (lengthMethods.containsKey(name)) {
                continue;
            }
            // Skip suffixed internal methods
            if (hasSkipSuffix(name)) {
                continue;
            }

            try {
                Object value = method.invoke(obj);
                result.put(name, convertValue(value));
            } catch (Exception e) {
                LOG.warn("Failed to read field '{}' on {}: {}",
                        name, clazz.getSimpleName(), e.getMessage());
            }
        }

        return result;
    }

    /** Convert a value returned by a FlatBuffer getter to a JSON-safe representation. */
    private Object convertValue(Object value) {
        if (value == null) {
            return null;
        }
        // Primitives and strings pass through directly
        if (value instanceof Number || value instanceof Boolean || value instanceof String) {
            return value;
        }
        // FlatBuffer Table → recurse
        if (value instanceof Table) {
            return tableToMap(value);
        }
        // Fallback: use toString
        return value.toString();
    }

    private boolean shouldSkipMethod(Method m, String name) {
        if (SKIP_METHODS.contains(name)) {
            return true;
        }
        if (name.startsWith("__")) {
            return true;
        }
        // Skip methods declared on Object or Table base class
        if (m.getDeclaringClass() == Object.class) {
            return true;
        }
        // Skip static methods (like getRootAs*, create*, finish*, etc.)
        if (java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
            return true;
        }
        // Skip methods that take a Table parameter (union fill methods)
        if (m.getParameterCount() == 1 && Table.class.isAssignableFrom(m.getParameterTypes()[0])) {
            return true;
        }
        return false;
    }

    private boolean hasSkipSuffix(String name) {
        for (String suffix : SKIP_SUFFIXES) {
            if (name.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }
}
