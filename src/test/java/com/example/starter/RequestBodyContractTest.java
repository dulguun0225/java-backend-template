package com.example.starter;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.starter.platform.error.BoundBody;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriTemplate;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * The shape every request body is held to, read by reflection over the main controllers. Each
 * {@code @RequestBody BoundBody<T>} names a record {@code T} that:
 *
 * <ul>
 *   <li>carries no member named after a path variable of its route — an identifier travels in the path only, not
 *       even as an optional echo the service compares;
 *   <li>carries no Jackson annotation that would widen what it accepts ({@code @JsonIgnoreProperties},
 *       {@code @JsonAnySetter}, {@code @JsonIgnore}), each of which would make the strict reader accept a member
 *       silently;
 *   <li>has only members of the three types the strict reader names a wrong type against — {@code String},
 *       {@code Boolean}, {@code Number} — or a member with its own {@code @JsonDeserialize}. {@code Integer} would
 *       silently truncate {@code 1.5} to {@code 1}; a {@code BigDecimal} or {@code Double} would let a decimal
 *       cross the wire as a JSON number;
 *   <li>is the body of that one handler alone: each operation binds its own request type, so an update type is
 *       never a create type with fields the update does not write.
 * </ul>
 *
 * <p>Every check runs a second time over {@code RequestBodyContractFixture}, where each handler breaks exactly
 * one of them, and must report exactly those handlers: a check that finds nothing there has no proof it can fire.
 */
class RequestBodyContractTest {

    static final String FIXTURES_PACKAGE = BanListNegativeControlTest.FIXTURES_PACKAGE;
    private static final String FIXTURE = "RequestBodyContractFixture";

    private static final Set<Class<?>> MEMBER_TYPES = Set.of(String.class, Boolean.class, Number.class);

    private static final Set<Class<?>> BANNED_MEMBER_TYPES =
            Set.of(int.class, long.class, Integer.class, Long.class, double.class, Double.class, BigDecimal.class);

    record BodyHandler(Method handler, Class<?> body, Set<String> pathVariables) {

        String name() {
            return handler.getDeclaringClass().getSimpleName() + "." + handler.getName();
        }
    }

    @Test
    void everyRequestBodyIsARecordNamingNoPathVariable() {
        assertThat(recordsNamingNoPathVariable(mainHandlers())).isEmpty();
    }

    @Test
    void noRequestBodyWidensWhatItAccepts() {
        assertThat(nothingWidensWhatItAccepts(mainHandlers())).isEmpty();
    }

    @Test
    void everyMemberIsAStringABooleanANumberOrReadByItsOwnDeserializer() {
        assertThat(membersOfTheNamedTypes(mainHandlers())).isEmpty();
    }

    @Test
    void noRecordIsTheRequestBodyOfTwoHandlers() {
        assertThat(oneHandlerPerRequestType(mainHandlers())).isEmpty();
    }

    /** The negative control: each check reports exactly the fixture handlers written to break it. */
    @Test
    void eachCheckReportsExactlyTheFixtureHandlersThatBreakIt() {
        List<BodyHandler> fixtures = bodyHandlers(new ClassFileImporter().importPackages(FIXTURES_PACKAGE)).stream()
                .filter(handler ->
                        handler.handler().getDeclaringClass().getSimpleName().equals(FIXTURE))
                .toList();
        assertThat(fixtures).as("the fixture's handlers were found").hasSize(8);

        assertThat(recordsNamingNoPathVariable(fixtures))
                .containsExactlyInAnyOrder(
                        FIXTURE + ".echo: /code is a path variable of the route",
                        FIXTURE + ".echo: /tenant is a path variable of the route",
                        FIXTURE + ".notARecord: " + FIXTURES_PACKAGE + "." + FIXTURE + "$NotARecord is not a record");
        assertThat(nothingWidensWhatItAccepts(fixtures))
                .containsExactlyInAnyOrder(
                        FIXTURE + ".ignoresUnknown: the type carries @JsonIgnoreProperties",
                        FIXTURE + ".ignoresMember: /hidden carries @JsonIgnore",
                        FIXTURE + ".collectsTheRest: rest is a @JsonAnySetter");
        assertThat(membersOfTheNamedTypes(fixtures))
                .containsExactlyInAnyOrder(
                        FIXTURE + ".wrongMemberTypes: /count is java.lang.Integer",
                        FIXTURE + ".wrongMemberTypes: /amount is java.math.BigDecimal",
                        FIXTURE + ".wrongMemberTypes: /tags is java.util.List");
        assertThat(oneHandlerPerRequestType(fixtures))
                .containsExactly(FIXTURES_PACKAGE + "." + FIXTURE + "$SharedBody is the body of " + FIXTURE
                        + ".sharedA, " + FIXTURE + ".sharedB");
    }

    static List<String> recordsNamingNoPathVariable(List<BodyHandler> handlers) {
        return violations(handlers, handler -> {
            List<String> found = new ArrayList<>();
            if (!handler.body().isRecord()) {
                found.add(handler.body().getName() + " is not a record");
                return found;
            }
            for (RecordComponent component : handler.body().getRecordComponents()) {
                if (handler.pathVariables().contains(component.getName())) {
                    found.add("/" + component.getName() + " is a path variable of the route");
                }
            }
            return found;
        });
    }

    static List<String> nothingWidensWhatItAccepts(List<BodyHandler> handlers) {
        return violations(handlers, handler -> {
            List<String> found = new ArrayList<>();
            Class<?> body = handler.body();
            if (body.isAnnotationPresent(JsonIgnoreProperties.class)) {
                found.add("the type carries @JsonIgnoreProperties");
            }
            for (Method method : body.getDeclaredMethods()) {
                if (method.isAnnotationPresent(JsonAnySetter.class)) {
                    found.add(method.getName() + " is a @JsonAnySetter");
                }
            }
            for (Field field : body.getDeclaredFields()) {
                if (field.isAnnotationPresent(JsonAnySetter.class)) {
                    found.add(field.getName() + " is a @JsonAnySetter");
                }
            }
            if (body.isRecord()) {
                for (RecordComponent component : body.getRecordComponents()) {
                    if (placesOf(body, component).stream()
                            .anyMatch(element -> element.isAnnotationPresent(JsonIgnore.class))) {
                        found.add("/" + component.getName() + " carries @JsonIgnore");
                    }
                }
            }
            return found;
        });
    }

    static List<String> membersOfTheNamedTypes(List<BodyHandler> handlers) {
        return violations(handlers, handler -> {
            List<String> found = new ArrayList<>();
            if (!handler.body().isRecord()) {
                return found;
            }
            for (RecordComponent component : handler.body().getRecordComponents()) {
                Class<?> type = component.getType();
                boolean ownDeserializer = placesOf(handler.body(), component).stream()
                        .anyMatch(element -> element.isAnnotationPresent(JsonDeserialize.class));
                if (BANNED_MEMBER_TYPES.contains(type) || !(MEMBER_TYPES.contains(type) || ownDeserializer)) {
                    found.add("/" + component.getName() + " is " + type.getName());
                }
            }
            return found;
        });
    }

    static List<String> oneHandlerPerRequestType(List<BodyHandler> handlers) {
        Map<Class<?>, List<String>> byType = new LinkedHashMap<>();
        for (BodyHandler handler : handlers) {
            byType.computeIfAbsent(handler.body(), type -> new ArrayList<>()).add(handler.name());
        }
        List<String> found = new ArrayList<>();
        byType.forEach((type, names) -> {
            if (names.size() > 1) {
                found.add(type.getName() + " is the body of " + String.join(", ", new TreeSet<>(names)));
            }
        });
        return found;
    }

    private static List<String> violations(List<BodyHandler> handlers, Function<BodyHandler, List<String>> check) {
        List<String> found = new ArrayList<>();
        for (BodyHandler handler : handlers) {
            check.apply(handler).forEach(violation -> found.add(handler.name() + ": " + violation));
        }
        return found;
    }

    private static List<BodyHandler> mainHandlers() {
        List<BodyHandler> handlers = bodyHandlers(new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BanListArchTest.BASE));
        assertThat(handlers).as("the main controllers declare request bodies").isNotEmpty();
        return handlers;
    }

    /**
     * Every request-mapped handler of every {@code @RestController} among {@code classes} that takes a
     * {@code @RequestBody}, with the type it binds — {@code T} of a {@code BoundBody<T>}, or the parameter's own
     * type where the handler binds its body raw (which {@code BanListArchTest.requestBodiesBindThroughBoundBody}
     * refuses) — and the variables of its route template, class and method mapping together, plus every
     * {@code @PathVariable} name.
     */
    static List<BodyHandler> bodyHandlers(JavaClasses classes) {
        List<BodyHandler> handlers = new ArrayList<>();
        for (JavaClass controller : classes) {
            if (!controller.isAnnotatedWith(RestController.class)) {
                continue;
            }
            Class<?> type = controller.reflect();
            for (Method method : type.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                Class<?> body = bodyOf(method);
                if (body != null) {
                    handlers.add(new BodyHandler(method, body, pathVariables(type, method, mapping)));
                }
            }
        }
        return handlers;
    }

    private static @Nullable Class<?> bodyOf(Method method) {
        Parameter[] parameters = method.getParameters();
        Type[] generic = method.getGenericParameterTypes();
        for (int i = 0; i < parameters.length; i++) {
            if (!parameters[i].isAnnotationPresent(RequestBody.class)) {
                continue;
            }
            if (parameters[i].getType() == BoundBody.class && generic[i] instanceof ParameterizedType bound) {
                Type argument = bound.getActualTypeArguments()[0];
                return argument instanceof Class<?> named
                        ? named
                        : (Class<?>) ((ParameterizedType) argument).getRawType();
            }
            return parameters[i].getType();
        }
        return null;
    }

    private static Set<String> pathVariables(Class<?> controller, Method method, RequestMapping mapping) {
        Set<String> names = new TreeSet<>();
        RequestMapping onClass = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
        List<String> templates = new ArrayList<>(List.of(mapping.path()));
        if (onClass != null) {
            templates.addAll(List.of(onClass.path()));
        }
        for (String template : templates) {
            for (String variable : new UriTemplate(template).getVariableNames()) {
                names.add(variable.startsWith("*") ? variable.substring(1) : variable);
            }
        }
        for (Parameter parameter : method.getParameters()) {
            PathVariable path = parameter.getAnnotation(PathVariable.class);
            if (path != null) {
                names.add(
                        !path.name().isEmpty()
                                ? path.name()
                                : !path.value().isEmpty() ? path.value() : parameter.getName());
            }
        }
        return names;
    }

    /** Where Jackson reads a record component's annotations: the component, its field and its accessor. */
    private static List<AnnotatedElement> placesOf(Class<?> record, RecordComponent component) {
        List<AnnotatedElement> places = new ArrayList<>();
        places.add(component);
        places.add(component.getAccessor());
        try {
            places.add(record.getDeclaredField(component.getName()));
        } catch (NoSuchFieldException e) {
            throw new AssertionError(record + " has no field for " + component.getName(), e);
        }
        return places;
    }
}
