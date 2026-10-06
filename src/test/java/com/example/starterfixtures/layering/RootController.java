package com.example.starterfixtures.layering;

import com.example.starterfixtures.layering.greeting.GreetingService;
import org.springframework.stereotype.Controller;

/**
 * A controller directly in the base package, outside every feature, declared by {@code @Controller} rather than
 * {@code @RestController}: the one violation besides the platform tier's that
 * {@code controllersLiveInFeaturePackages} must report. Its call into feature {@code greeting} is not reported:
 * the base package belongs to no slice.
 */
@Controller
class RootController {
    String call(GreetingService greeting) {
        return greeting.greet();
    }
}
