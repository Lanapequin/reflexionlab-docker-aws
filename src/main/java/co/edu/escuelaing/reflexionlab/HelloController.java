package co.edu.escuelaing.reflexionlab;

/**
 * Controlador de ejemplo simple.
 * Demuestra el uso básico de @RestController y @GetMapping.
 */
@RestController
public class HelloController {

    @GetMapping("/")
    public String index() {
        return "Hola mundo desde MicroSpringBoot!";
    }

    @GetMapping("/hello")
    public String hello() {
        return "Hello from MicroSpringBoot Framework!";
    }
}