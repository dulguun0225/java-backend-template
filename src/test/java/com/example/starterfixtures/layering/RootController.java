package com.example.starterfixtures.layering;

import org.springframework.stereotype.Controller;

/**
 * A controller directly in the base package, outside every feature, declared by {@code @Controller} rather than
 * {@code @RestController}: the one violation besides the platform tier's that
 * {@code controllersLiveInFeaturePackages} must report.
 */
@Controller
class RootController {}
