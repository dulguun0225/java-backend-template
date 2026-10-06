package com.example.starterfixtures.layering.db;

import com.example.starterfixtures.layering.greeting.GreetingService;

/**
 * Stands in for a generated class naming a feature's type, as a jOOQ forced type's converter would: reported by
 * {@code generatedTreeDependsOnNothingOutsideIt}. Its reference to {@code GeneratedTable} stays inside the tree
 * and is not.
 */
public final class GeneratedTableNamesAFeature {
    private GeneratedTableNamesAFeature() {}

    public static String name(GreetingService greeting) {
        return greeting.greet() + GeneratedTable.name();
    }
}
