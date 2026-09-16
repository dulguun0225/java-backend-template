package com.example.starter;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackages;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.dependencies.Slice;
import org.junit.jupiter.api.Test;

/**
 * Package layering. The platform tier depends on no feature; features depend on the platform tier and never
 * on each other; the generated jOOQ tree is shared infrastructure any feature may read. A feature package is a
 * direct child of the base package other than {@code platform} and {@code db}.
 */
class LayeringArchTest {

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BanListArchTest.BASE);

    @Test
    void platformDependsOnNoFeature() {
        noClasses()
                .that()
                .resideInAPackage(BanListArchTest.PLATFORM)
                .should()
                .dependOnClassesThat(resideInAPackage(BanListArchTest.BASE + "..")
                        .and(resideOutsideOfPackages(BanListArchTest.PLATFORM, BanListArchTest.GENERATED)))
                .because("the platform tier is the foundation; a feature dependency would invert the layering")
                .check(MAIN);
    }

    @Test
    void featuresDoNotDependOnEachOther() {
        slices().matching(BanListArchTest.BASE + ".(*)..")
                .that(DescribedPredicate.<Slice>describe(
                        "feature slices",
                        slice -> !slice.getDescription().contains("platform")
                                && !slice.getDescription().contains("db")))
                .should()
                .notDependOnEachOther()
                .because(
                        "features integrate through the platform tier or over the wire, never through each other's classes")
                .check(MAIN);
    }

    @Test
    void controllersLiveInFeaturePackages() {
        classes()
                .that()
                .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .should()
                .resideOutsideOfPackages(BanListArchTest.PLATFORM, BanListArchTest.GENERATED)
                .because("the platform tier has no HTTP surface")
                .check(MAIN);
    }
}
