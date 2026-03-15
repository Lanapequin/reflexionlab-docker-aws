import co.edu.escuelaing.reflexionlab.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * Tests automatizados para el framework MicroSpringBoot.
 * Verifican el comportamiento de la reflexión y las anotaciones.
 */
public class MicroSpringBootTest {

    // 1. Pruebas de anotaciones

    @Test
    public void testRestControllerAnnotationPresent() {
        // HelloController debe tener @RestController
        assertTrue(
                HelloController.class.isAnnotationPresent(RestController.class),
                "HelloController debe estar anotado con @RestController"
        );
    }

    @Test
    public void testGreetingControllerAnnotationPresent() {
        assertTrue(
                GreetingController.class.isAnnotationPresent(RestController.class),
                "GreetingController debe estar anotado con @RestController"
        );
    }

    @Test
    public void testGetMappingAnnotationOnIndexMethod() throws Exception {
        Method index = HelloController.class.getDeclaredMethod("index");
        assertTrue(
                index.isAnnotationPresent(GetMapping.class),
                "El método index() debe tener @GetMapping"
        );
        assertEquals("/", index.getAnnotation(GetMapping.class).value(),
                "El path de index() debe ser '/'");
    }

    @Test
    public void testGetMappingAnnotationOnGreetingMethod() throws Exception {
        Method greeting = GreetingController.class.getDeclaredMethod("greeting", String.class);
        assertTrue(
                greeting.isAnnotationPresent(GetMapping.class),
                "El método greeting() debe tener @GetMapping"
        );
        assertEquals("/greeting", greeting.getAnnotation(GetMapping.class).value(),
                "El path de greeting() debe ser '/greeting'");
    }

    // 2. Pruebas de @RequestParam

    @Test
    public void testRequestParamAnnotationPresent() throws Exception {
        Method greeting = GreetingController.class.getDeclaredMethod("greeting", String.class);
        Parameter[] params = greeting.getParameters();
        assertEquals(1, params.length, "greeting() debe tener exactamente 1 parámetro");
        assertTrue(
                params[0].isAnnotationPresent(RequestParam.class),
                "El parámetro debe tener @RequestParam"
        );
    }

    @Test
    public void testRequestParamDefaultValue() throws Exception {
        Method greeting = GreetingController.class.getDeclaredMethod("greeting", String.class);
        RequestParam rp = greeting.getParameters()[0].getAnnotation(RequestParam.class);
        assertEquals("name", rp.value(), "El nombre del param debe ser 'name'");
        assertEquals("World", rp.defaultValue(), "El valor por defecto debe ser 'World'");
    }

    // 3. Pruebas de invocación por reflexión

    @Test
    public void testInvokeIndexByReflection() throws Exception {
        HelloController controller = new HelloController();
        Method index = HelloController.class.getDeclaredMethod("index");
        String result = (String) index.invoke(controller);
        assertNotNull(result, "El resultado no debe ser null");
        assertFalse(result.isEmpty(), "El resultado no debe estar vacío");
    }

    @Test
    public void testInvokeGreetingWithDefaultValue() throws Exception {
        GreetingController controller = new GreetingController();
        Method greeting = GreetingController.class.getDeclaredMethod("greeting", String.class);
        String result = (String) greeting.invoke(controller, "World");
        assertEquals("Hola World", result, "Con nombre 'World' debe retornar 'Hola World'");
    }

    @Test
    public void testInvokeGreetingWithCustomName() throws Exception {
        GreetingController controller = new GreetingController();
        Method greeting = GreetingController.class.getDeclaredMethod("greeting", String.class);
        String result = (String) greeting.invoke(controller, "Ana");
        assertEquals("Hola Ana", result, "Con nombre 'Ana' debe retornar 'Hola Ana'");
    }

    // 4. Prueba de carga dinámica de clase por reflexión (Class.forName)

    @Test
    public void testLoadControllerByReflection() throws Exception {
        Class<?> clazz = Class.forName("co.edu.escuelaing.reflexionlab.HelloController");
        assertTrue(clazz.isAnnotationPresent(RestController.class),
                "La clase cargada dinámicamente debe tener @RestController");
        Object instance = clazz.getDeclaredConstructor().newInstance();
        assertNotNull(instance, "La instancia creada por reflexión no debe ser null");
    }

    @Test
    public void testGetMappingMethodsCount() {
        long count = java.util.Arrays.stream(HelloController.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(GetMapping.class))
                .count();
        assertTrue(count >= 1, "HelloController debe tener al menos 1 método con @GetMapping");
    }
}