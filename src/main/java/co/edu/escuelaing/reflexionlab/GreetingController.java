package co.edu.escuelaing.reflexionlab;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Controlador que demuestra el uso de @RequestParam con valor por defecto.
 * GET /greeting          -> "Hola World!"
 * GET /greeting?name=Ana -> "Hola Ana!"
 */
@RestController
public class GreetingController {

    private static final String template = "Hello, %s!";
    private final AtomicLong counter = new AtomicLong();

    @GetMapping("/greeting")
    public String greeting(@RequestParam(value = "name", defaultValue = "World") String name) {
        return "Hola " + name;
    }

    @GetMapping("/counter")
    public String counter() {
        return "Visitas: " + counter.incrementAndGet();
    }
}