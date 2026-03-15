package co.edu.escuelaing.reflexionlab;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca una clase como controlador REST.
 * El framework IoC detectará automáticamente estas clases
 * y registrará sus métodos como servicios web.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RestController {
}