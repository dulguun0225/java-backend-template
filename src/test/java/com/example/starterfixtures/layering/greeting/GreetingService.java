package com.example.starterfixtures.layering.greeting;

import com.example.starterfixtures.layering.db.GeneratedTable;
import com.example.starterfixtures.layering.platform.PlatformReadsGeneratedTree;

/** The feature the other fixture features reach into. */
public final class GreetingService {
    public String greet() {
        return GeneratedTable.name() + PlatformReadsGeneratedTree.name();
    }
}
