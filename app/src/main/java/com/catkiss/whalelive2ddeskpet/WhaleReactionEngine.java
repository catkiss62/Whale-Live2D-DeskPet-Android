package com.catkiss.whalelive2ddeskpet;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Semantic layer for the later AI companion. Reactions only reference author-supplied catalog
 * entries; they do not replace or rewrite the purchased model files.
 */
final class WhaleReactionEngine {
    interface Target {
        void resetExpressions();
        void enableExpression(String id);
        void playMotion(String id);
    }

    static final List<Reaction> REACTIONS = Collections.unmodifiableList(Arrays.asList(
            reaction("hello", "打招呼", "开心兴奋", "motions/自拍简单"),
            reaction("celebrate", "开心庆祝", "开心兴奋|双手比耶", ""),
            reaction("affection", "喜欢你", "爱心眼|love", ""),
            reaction("shy", "害羞", "脸红|情绪花花", ""),
            reaction("curious", "疑惑", "问号", ""),
            reaction("surprised", "惊讶", "感叹号", ""),
            reaction("sad", "难过", "悲伤", ""),
            reaction("cry", "大哭", "哭", ""),
            reaction("angry", "生气重锤", "生气", "aidale"),
            reaction("dizzy", "晕乎乎", "晕晕", ""),
            reaction("playful", "调皮吐舌", "调皮|吐舌", ""),
            reaction("comfort", "贴纸安慰", "猫猫贴纸|兔兔贴纸", ""),
            reaction("food_fun", "蛋包饭玩耍", "蛋包饭", "motions/番茄酱")
    ));

    private WhaleReactionEngine() { }

    static void apply(String id, Target target) {
        Reaction reaction = find(id);
        if (reaction == null || target == null) return;
        target.resetExpressions();
        for (String expression : reaction.expressions) target.enableExpression(expression);
        if (!reaction.motionId.isEmpty()) target.playMotion(reaction.motionId);
    }

    static Reaction find(String id) {
        if (id == null) return null;
        for (Reaction reaction : REACTIONS) if (reaction.id.equals(id)) return reaction;
        return null;
    }

    static boolean isAvailable(Reaction reaction, WhaleCatalog catalog) {
        if (reaction == null || catalog == null) return false;
        for (String expression : reaction.expressions) {
            if (catalog.expression(expression) == null) return false;
        }
        return reaction.motionId.isEmpty() || catalog.motion(reaction.motionId) != null;
    }

    static int availableCount(WhaleCatalog catalog) {
        int count = 0;
        for (Reaction reaction : REACTIONS) if (isAvailable(reaction, catalog)) count++;
        return count;
    }

    private static Reaction reaction(String id, String label,
                                     String expressionIds, String motionId) {
        String[] expressions = expressionIds.isEmpty()
                ? new String[0] : expressionIds.split("\\|");
        return new Reaction(id, label, Collections.unmodifiableList(Arrays.asList(expressions)),
                motionId);
    }

    static final class Reaction {
        final String id;
        final String label;
        final List<String> expressions;
        final String motionId;

        Reaction(String id, String label, List<String> expressions, String motionId) {
            this.id = id;
            this.label = label;
            this.expressions = expressions;
            this.motionId = motionId;
        }
    }
}
