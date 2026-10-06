package com.example.starterfixtures.layering.platform;

import com.example.starterfixtures.layering.db.GeneratedTable;

/** The platform tier reads the generated tree: not reported, since the generated tree is shared infrastructure. */
public final class PlatformReadsGeneratedTree {
    private PlatformReadsGeneratedTree() {}

    public static String name() {
        return GeneratedTable.name();
    }
}
