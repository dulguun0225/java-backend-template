package com.example.starterfixtures.layering.greeting;

import com.example.starterfixtures.layering.db.GeneratedTable;
import com.example.starterfixtures.layering.platform.PlatformReadsGeneratedTree;

/** Internal to {@code greeting}: outside its {@code api} package, so no other feature may call it. */
public final class GreetingService {
    public String greet() {
        return GeneratedTable.name() + PlatformReadsGeneratedTree.name();
    }
}
