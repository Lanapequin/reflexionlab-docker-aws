package co.edu.escuelaing.reflexionlab;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mapea un parámetro de query string a un parámetro de método.
 * Soporta valor por defecto con defaultValue.
 * Uso: @RequestParam(value = "name", defaultValue = "World")
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface RequestParam {
    String value();
    String defaultValue() default "";
}