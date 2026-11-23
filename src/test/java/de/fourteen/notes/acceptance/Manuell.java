package de.fourteen.notes.acceptance;

import com.tngtech.jgiven.annotation.IsTag;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A scenario without an automated test, like `@manuell("<reason>")` in the Gherkin; the report
 * shows the reason.
 */
@IsTag(name = "manuell")
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Manuell {
    String[] value();
}
