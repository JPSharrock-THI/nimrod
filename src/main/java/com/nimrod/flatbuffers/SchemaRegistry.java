package com.nimrod.flatbuffers;

import com.google.flatbuffers.Table;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of FlatBuffer root table types from the sup-server-db-fbs-schema artifact.
 *
 * <p>At startup, registers all known root types and exposes lookup by file_identifier.
 * Each blob's 4-byte identifier (bytes 4–7) is checked against each registered class's
 * {@code *BufferHasIdentifier(ByteBuffer)} method to find the correct deserializer.</p>
 */
@Component
public class SchemaRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaRegistry.class);

    /**
     * All known FlatBuffer root table classes from the sup-server-db-fbs-schema artifact.
     * Each entry is the fully-qualified class name. The class must have:
     * <ul>
     *   <li>a static {@code getRootAs<Name>(ByteBuffer)} method</li>
     *   <li>a static {@code <Name>BufferHasIdentifier(ByteBuffer)} method</li>
     * </ul>
     */
    private static final List<String> ROOT_CLASS_NAMES = List.of(
            "com.bytro.sup.fbs.db.ai.FbsDbAiProfile",
            "com.bytro.sup.fbs.db.army.FbsDbArmy",
            "com.bytro.sup.fbs.db.buildqueue.FbsDbBuildQueue",
            "com.bytro.sup.fbs.db.event.FbsDbServerEvent",
            "com.bytro.sup.fbs.db.foreign.FbsDbForeignAffairRelations",
            "com.bytro.sup.fbs.db.gameevent.FbsDbGameEvent",
            "com.bytro.sup.fbs.db.goal.FbsDbGameGoal",
            "com.bytro.sup.fbs.db.mad.FbsDbLoginLogEntry",
            "com.bytro.sup.fbs.db.mad.FbsDbLoginLogState",
            "com.bytro.sup.fbs.db.map.FbsDbProvince",
            "com.bytro.sup.fbs.db.news.FbsDbNewsArticle",
            "com.bytro.sup.fbs.db.oldnews.FbsDbNewsReportArticleItem",
            "com.bytro.sup.fbs.db.player.FbsDbPlayerProfile",
            "com.bytro.sup.fbs.db.quest.FbsDbQuest",
            "com.bytro.sup.fbs.db.research.FbsDbResearchState",
            "com.bytro.sup.fbs.db.resource.profile.FbsDbResourceProfile",
            "com.bytro.sup.fbs.db.resource.state.FbsDbResourceState",
            "com.bytro.sup.fbs.db.spy.FbsDbDetailedSpyState",
            "com.bytro.sup.fbs.db.team.FbsDbTeamProfile",
            "com.bytro.sup.fbs.db.trade.profile.FbsDbTradingProfile",
            "com.bytro.sup.fbs.db.trade.state.FbsDbTradeState",
            "com.bytro.sup.fbs.db.tutorial.FbsDbTutorialState"
    );

    public record SchemaEntry(
            Class<?> rootClass,
            Method getRootMethod,
            Method hasIdentifierMethod
    ) {
        public String simpleName() {
            return rootClass.getSimpleName();
        }

        public String packageName() {
            return rootClass.getPackageName();
        }

        /** Check whether the given buffer matches this schema's file_identifier. */
        public boolean matches(ByteBuffer buffer) {
            try {
                return (boolean) hasIdentifierMethod.invoke(null, buffer.duplicate());
            } catch (Exception e) {
                return false;
            }
        }

        /** Deserialize the buffer into the FlatBuffer root Table object. */
        public Table deserialize(ByteBuffer buffer) {
            try {
                return (Table) getRootMethod.invoke(null, buffer);
            } catch (Exception e) {
                throw new RuntimeException(
                        "Failed to deserialize buffer as " + simpleName(), e);
            }
        }
    }

    private final Map<String, SchemaEntry> entriesByName = new LinkedHashMap<>();

    public SchemaRegistry() {
        for (String className : ROOT_CLASS_NAMES) {
            try {
                Class<?> clazz = Class.forName(className);
                String simpleName = clazz.getSimpleName();

                Method getRootMethod = clazz.getMethod(
                        "getRootAs" + simpleName, ByteBuffer.class);
                Method hasIdMethod = clazz.getMethod(
                        simpleName + "BufferHasIdentifier", ByteBuffer.class);

                entriesByName.put(simpleName, new SchemaEntry(clazz, getRootMethod, hasIdMethod));
                LOG.debug("Registered FBS schema: {}", simpleName);
            } catch (ClassNotFoundException e) {
                LOG.warn("FBS schema class not found on classpath: {}", className);
            } catch (NoSuchMethodException e) {
                LOG.warn("FBS schema class missing expected methods: {} ({})",
                        className, e.getMessage());
            }
        }
        LOG.info("Loaded {} FBS schemas", entriesByName.size());
    }

    /**
     * Find the schema entry whose file_identifier matches the given FlatBuffer blob.
     * Iterates all registered schemas and calls their {@code BufferHasIdentifier} method.
     *
     * @return matching entry, or empty if no schema matches
     */
    public Optional<SchemaEntry> findByBuffer(ByteBuffer buffer) {
        for (SchemaEntry entry : entriesByName.values()) {
            if (entry.matches(buffer)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /** @return all registered schemas, keyed by simple class name */
    public Map<String, SchemaEntry> getAllSchemas() {
        return Collections.unmodifiableMap(entriesByName);
    }

    /**
     * Format a table of all registered schemas for display.
     * Used by the {@code schemas} subcommand and in error messages.
     */
    public String formatSchemaList() {
        if (entriesByName.isEmpty()) {
            return "No FBS schemas registered.";
        }
        var sb = new StringBuilder();
        sb.append(String.format("%-40s %s%n", "Root Type", "Package"));
        sb.append(String.format("%-40s %s%n", "-".repeat(40), "-".repeat(40)));
        for (SchemaEntry entry : entriesByName.values()) {
            sb.append(String.format("%-40s %s%n", entry.simpleName(), entry.packageName()));
        }
        sb.append(String.format("%n%d schemas registered%n", entriesByName.size()));
        return sb.toString();
    }
}
