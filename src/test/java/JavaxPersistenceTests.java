import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.PersistenceUnit;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.domain.properties.HasType;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchIgnore;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchUnitRunner;
import com.tngtech.archunit.lang.AbstractClassesTransformer;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ClassesTransformer;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.runner.RunWith;

import static com.tngtech.archunit.core.domain.Formatters.formatLocation;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasType.Functions.GET_TYPE;
import static com.tngtech.archunit.lang.Priority.LOW;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.no;

@RunWith(ArchUnitRunner.class)
@AnalyzeClasses(packages = "org.superbiz.servlet")
public class JavaxPersistenceTests {

    private static final DescribedPredicate<JavaFieldAccess> TARGET_IS_ID =
            JavaAccess.Functions.Get.<JavaFieldAccess, AccessTarget.FieldAccessTarget>target()
                    .is(annotatedWith(Id.class));

    private static final DescribedPredicate<JavaFieldAccess> TARGET_IS_COLUMN =
            JavaAccess.Functions.Get.<JavaFieldAccess, AccessTarget.FieldAccessTarget>target()
                    .is(annotatedWith(Column.class));

    private static final DescribedPredicate<JavaFieldAccess> TARGET_IS_GENERATED_VALUE =
            JavaAccess.Functions.Get.<JavaFieldAccess, AccessTarget.FieldAccessTarget>target()
                    .is(annotatedWith(GeneratedValue.class));

    private static final DescribedPredicate<JavaFieldAccess> TARGET_IS_PERSISTENCE_UNIT =
            JavaAccess.Functions.Get.<JavaFieldAccess, AccessTarget.FieldAccessTarget>target()
                    .is(annotatedWith(PersistenceUnit.class));

    @ArchIgnore
    @ArchTest
    public static final ArchRule noPersistenceAtAll = ArchRuleDefinition.priority(LOW).noClasses()
            .should().accessClassesThat().resideInAPackage("javax.persistence..");

    @ArchTest
    public static final ArchRule noEntities = ArchRuleDefinition.priority(LOW).noClasses()
            .should().beAnnotatedWith(GET_TYPE.is(nameMatching("javax\\.persistence\\.Entity")));

    /**
     * False Positive. @Id is set to Retention.RUNTIME, so should be OK?
     * @see org.superbiz.servlet.JpaBean
     */
    @ArchTest
    public static final ArchRule noIdFields = ArchRuleDefinition.priority(LOW)
            .noClasses().should().accessFieldWhere(TARGET_IS_ID);

    @ArchTest
    public static final ArchRule noColumnFields = ArchRuleDefinition.priority(LOW)
            .noClasses().should().accessFieldWhere(TARGET_IS_COLUMN);

    /**
     * False Positive. @Generated value is set to Retention.RUNTIME, so should be OK?
     * @see org.superbiz.servlet.JpaBean
     */
    @ArchTest
    public static final ArchRule noGeneratedValues = ArchRuleDefinition.priority(LOW)
            .noClasses().should().accessFieldWhere(TARGET_IS_GENERATED_VALUE);

    /**
     * False Positive. @PersistenceUnit is set to Retention.RUNTIME, so should be OK?
     * @see org.superbiz.servlet.JpaServlet
     */
    @ArchTest
    public static final ArchRule noPersistenceUnits = ArchRuleDefinition.priority(LOW)
            .noClasses().should().accessFieldWhere(TARGET_IS_PERSISTENCE_UNIT);

    @ArchTest
    public static final ArchRule noPersistenceAnnotationsOnMembers =
            no(members()).should(beAnnotatedWithTypeIn("javax.persistence.."));

    private static ClassesTransformer<JavaMember> members() {
        return new AbstractClassesTransformer<JavaMember>("members") {
            @Override
            public Iterable<JavaMember> doTransform(JavaClasses javaClasses) {
                Set<JavaMember> result = new HashSet<>();
                for (JavaClass javaClass : javaClasses) {
                    result.addAll(javaClass.getMembers());
                }
                return result;
            }
        };
    }

    private static ArchCondition<JavaMember> beAnnotatedWithTypeIn(final String packageIdentifier) {
        return new ArchCondition<JavaMember>("be annotated with type in javax.persistence..") {
            @Override
            public void check(JavaMember javaMember, ConditionEvents conditionEvents) {
                Set<JavaAnnotation> matchingAnnotations = getAnnotationsWithType(javaMember, packageIdentifier);
                boolean satisfied = !matchingAnnotations.isEmpty();
                String message = String.format("member %s is annotated with %s in %s",
                        javaMember.getFullName(), toString(matchingAnnotations), formatLocation(javaMember.getOwner(), 0));
                conditionEvents.add(new SimpleConditionEvent(javaMember, satisfied, message));
            }

            private Set<JavaAnnotation> getAnnotationsWithType(JavaMember javaMember, String packageIdentifier) {
                final DescribedPredicate<HasType> inJavaxPersistence = GET_TYPE.is(resideInAPackage(packageIdentifier));
                Set<JavaAnnotation> result = new HashSet<>();
                for (JavaAnnotation annotation : javaMember.getAnnotations()) {
                    if (inJavaxPersistence.apply(annotation)) {
                        result.add(annotation);
                    }
                }
                return result;
            }

            private Set<String> toString(Set<JavaAnnotation> matchingAnnotations) {
                Set<String> result = new TreeSet<>();
                for (JavaAnnotation annotation : matchingAnnotations) {
                    result.add("@" + annotation.getType().getName());
                }
                return result;
            }
        };
    }
}
