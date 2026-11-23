package de.fourteen.notes;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.targetOwner;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import de.fourteen.notes.domain.DomainException;

/**
 * Rules beyond the rings of {@link OnionArchitectureTest}, checked on the compiled main classes
 * with ArchUnit.
 */
@AnalyzeClasses(
        packages = "de.fourteen.notes",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    private static final String DOMAIN = "de.fourteen.notes.domain..";
    private static final String APPLICATION = "de.fourteen.notes.application..";
    private static final String INFRASTRUCTURE = "de.fourteen.notes.infrastructure..";
    private static final String WEB = "de.fourteen.notes.web..";

    @ArchTest
    static final ArchRule controllers_live_in_web =
            classes()
                    .that()
                    .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                    .or()
                    .areAnnotatedWith("org.springframework.stereotype.Controller")
                    .should()
                    .resideInAPackage(WEB);

    @ArchTest
    static final ArchRule controllers_call_the_application_not_the_ports =
            noClasses()
                    .that()
                    .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                    .should()
                    .dependOnClassesThat()
                    .haveSimpleNameContaining("Repository")
                    .orShould()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName("de.fourteen.notes.domain.GitHub");

    @ArchTest
    static final ArchRule ports_are_interfaces_in_domain =
            classes()
                    .that()
                    .haveFullyQualifiedName("de.fourteen.notes.domain.NoteRepository")
                    .or()
                    .haveFullyQualifiedName("de.fourteen.notes.domain.GitHub")
                    .should()
                    .beInterfaces()
                    .andShould()
                    .resideInAPackage(DOMAIN);

    @ArchTest
    static final ArchRule ports_are_implemented_only_in_infrastructure =
            classes()
                    .that()
                    .implement("de.fourteen.notes.domain.NoteRepository")
                    .or()
                    .implement("de.fourteen.notes.domain.GitHub")
                    .should()
                    .resideInAPackage(INFRASTRUCTURE);

    @ArchTest
    static final ArchRule no_cycles_between_rings =
            slices().matching("de.fourteen.notes.(*)..").should().beFreeOfCycles();

    @ArchTest
    static final ArchRule domain_and_application_fail_with_domain_exceptions =
            noClasses()
                    .that()
                    .resideInAnyPackage(DOMAIN, APPLICATION)
                    .and()
                    .areNotAssignableTo(DomainException.class)
                    .should()
                    .callConstructorWhere(
                            targetOwner(
                                    assignableTo(Throwable.class)
                                            .and(not(assignableTo(DomainException.class)))
                                            .and(
                                                    resideInAPackage("java.lang")
                                                            .or(resideInAPackage("java.util")))));
}
