package com.example.starterfixtures.layering.db;

import com.example.starterfixtures.layering.platform.PlatformReadsGeneratedTree;

/**
 * Stands in for a generated class naming a platform class, as a jOOQ forced type's converter would; with the
 * platform tier reading the tree, a cycle. Reported by {@code generatedTreeDependsOnNothingOutsideIt}.
 */
public final class GeneratedTableNamesThePlatform {
    private GeneratedTableNamesThePlatform() {}

    public static String name() {
        return PlatformReadsGeneratedTree.name();
    }
}
