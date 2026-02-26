package com.nimrod.flatbuffers;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Registers GraalVM native-image reflection hints for all FBS root table classes.
 *
 * <p>These classes are loaded via {@link Class#forName} in {@link SchemaRegistry} and
 * walked reflectively in {@link FbDecoder}, so they must be registered for reflection
 * at build time for native image compilation.</p>
 */
public class FbsReflectionHints implements RuntimeHintsRegistrar {

    private static final String[] FBS_ROOT_CLASSES = {
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
            "com.bytro.sup.fbs.db.tutorial.FbsDbTutorialState",
    };

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        for (String className : FBS_ROOT_CLASSES) {
            try {
                Class<?> clazz = Class.forName(className, false, classLoader);
                hints.reflection().registerType(clazz,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.INVOKE_PUBLIC_METHODS);
            } catch (ClassNotFoundException e) {
                // Schema class not on classpath — skip (same as SchemaRegistry behaviour)
            }
        }
    }
}
