package co.edu.escuelaing.reflexionlab;

import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MicroSpringBoot - Mini framework IoC con reflexión y anotaciones.
 *
 * Mejoras respecto a la versión anterior:
 * - Soporte de solicitudes CONCURRENTES mediante un ThreadPool fijo (ExecutorService)
 * - Apagado ELEGANTE via Runtime shutdown hook: cierra el ServerSocket, espera que
 *   las tareas en vuelo terminen y libera el pool (ver: https://www.baeldung.com/jvm-shutdown-hooks)
 * - Escanea el classpath buscando clases con @RestController
 * - Registra métodos anotados con @GetMapping como rutas HTTP
 * - Soporta @RequestParam con defaultValue
 * - Sirve archivos estáticos (HTML, PNG, CSS, JS) desde /webroot
 */
public class MicroSpringBoot {

    private static final int PORT = 35000;
    private static final String STATIC_DIR = "src/main/resources/webroot";

    // Número de hilos concurrentes en el pool
    private static final int THREAD_POOL_SIZE = 10;
    private static final Map<String, Method> services  = new HashMap<>();
    private static final Map<String, Object> instances = new HashMap<>();
    private static final AtomicBoolean running = new AtomicBoolean(true);
    private static ExecutorService threadPool;
    private static ServerSocket serverSocket;

    // Arranque

    public static void main(String[] args) throws Exception {

        loadController(HelloController.class);
        loadController(GreetingController.class);

        registerShutdownHook();
        startServer();
    }

    // Apagado elegante

    /**
     * Registra un hook de JVM que se activa en un hilo separado cuando el proceso
     * recibe SIGTERM o el usuario presiona Ctrl+C.
     * Referencia: https://www.baeldung.com/jvm-shutdown-hooks
     */
    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Shutdown] Señal de apagado recibida. Cerrando servidor...");

            running.set(false);

            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                    System.out.println("[Shutdown] ServerSocket cerrado.");
                }
            } catch (IOException e) {
                System.err.println("[Shutdown] Error cerrando ServerSocket: " + e.getMessage());
            }

            if (threadPool != null) {
                threadPool.shutdown();
                try {
                    if (!threadPool.awaitTermination(30, TimeUnit.SECONDS)) {
                        System.out.println("[Shutdown] Tiempo de espera agotado. Forzando cierre...");
                        threadPool.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    threadPool.shutdownNow();
                    Thread.currentThread().interrupt();
                }
                System.out.println("[Shutdown] Pool de hilos terminado.");
            }

            System.out.println("[Shutdown] Servidor apagado correctamente.");
        }, "shutdown-hook"));
    }

    // Carga de controladores por reflexión

    private static void loadController(Class<?> clazz) throws Exception {
        if (!clazz.isAnnotationPresent(RestController.class)) {
            System.out.println("AVISO: " + clazz.getName() + " no tiene @RestController, omitiendo.");
            return;
        }

        Object instance = clazz.getDeclaredConstructor().newInstance();

        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(GetMapping.class)) {
                String path = method.getAnnotation(GetMapping.class).value();
                services.put(path, method);
                instances.put(path, instance);
                System.out.println("  [GET] " + path + "  ->  " + clazz.getSimpleName() + "#" + method.getName());
            }
        }
    }

    private static void scanAndLoad() {
        System.out.println("Escaneando classpath en busca de @RestController...");
        File classesDir = new File("target/classes");
        if (!classesDir.exists()) {
            System.out.println("Directorio target/classes no encontrado. Compile primero con: mvn compile");
            return;
        }
        scanDirectory(classesDir, classesDir.getPath());
    }

    private static void scanDirectory(File dir, String basePath) {
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                scanDirectory(file, basePath);
            } else if (file.getName().endsWith(".class")) {
                String className = file.getPath()
                        .replace(basePath + File.separator, "")
                        .replace(File.separator, ".")
                        .replace(".class", "");
                try {
                    Class<?> clazz = Class.forName(className);
                    if (clazz.isAnnotationPresent(RestController.class)) {
                        System.out.println("Encontrado: " + className);
                        loadController(clazz);
                    }
                } catch (Exception e) {

                }
            }
        }
    }

    // Servidor HTTP concurrente

    private static void startServer() throws IOException {
        threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        serverSocket = new ServerSocket(PORT);
        System.out.println("\nServidor MicroSpringBoot iniciado en http://localhost:" + PORT);
        System.out.println("Rutas registradas: " + services.keySet());
        System.out.println("Hilos concurrentes: " + THREAD_POOL_SIZE);
        System.out.println("Presiona Ctrl+C para detener.\n");

        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                threadPool.submit(() -> {
                    try {
                        handleRequest(clientSocket);
                    } catch (IOException e) {
                        System.err.println("[Error] Manejando solicitud: " + e.getMessage());
                    }
                });
            } catch (IOException e) {
                if (!running.get()) {
                    System.out.println("[Server] Bucle de aceptación terminado.");
                    break;
                }
                System.err.println("[Error] Aceptando conexión: " + e.getMessage());
            }
        }
    }

    // Manejo de solicitudes HTTP

    private static void handleRequest(Socket clientSocket) throws IOException {
        InputStream  in  = clientSocket.getInputStream();
        OutputStream out = clientSocket.getOutputStream();

        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String requestLine = reader.readLine();
        if (requestLine == null || requestLine.isEmpty()) {
            clientSocket.close();
            return;
        }

        System.out.println("[" + Thread.currentThread().getName() + "] >> " + requestLine);

        String[] parts = requestLine.split(" ");
        if (parts.length < 2) {
            clientSocket.close();
            return;
        }

        String rawUri = parts[1];
        URI uri;
        try {
            uri = new URI(rawUri);
        } catch (Exception e) {
            sendError(out, 400, "Bad Request");
            clientSocket.close();
            return;
        }

        String path  = uri.getPath();
        String query = uri.getRawQuery();

        if (services.containsKey(path)) {
            handleServiceRequest(out, path, query);
        } else if (serveStaticFile(out, path)) {

        } else {
            sendError(out, 404, "Not Found: " + path);
        }

        out.flush();
        clientSocket.close();
    }

    // Despacho de servicios dinámicos con @RequestParam

    private static void handleServiceRequest(OutputStream out, String path, String query) throws IOException {
        Method method   = services.get(path);
        Object instance = instances.get(path);

        try {
            Map<String, String> queryParams = parseQueryParams(query);
            Parameter[] parameters = method.getParameters();
            Object[]    args       = new Object[parameters.length];

            for (int i = 0; i < parameters.length; i++) {
                Parameter param = parameters[i];
                if (param.isAnnotationPresent(RequestParam.class)) {
                    RequestParam rp        = param.getAnnotation(RequestParam.class);
                    String paramName       = rp.value();
                    String defaultValue    = rp.defaultValue();
                    args[i] = queryParams.getOrDefault(paramName, defaultValue);
                } else {
                    args[i] = null;
                }
            }

            String responseBody = (String) method.invoke(instance, args);
            sendResponse(out, 200, "text/html; charset=UTF-8", responseBody.getBytes(StandardCharsets.UTF_8));

        } catch (Exception e) {
            e.printStackTrace();
            sendError(out, 500, "Internal Server Error: " + e.getMessage());
        }
    }

    // Archivos estáticos

    private static boolean serveStaticFile(OutputStream out, String path) throws IOException {
        if (path.equals("/")) path = "/index.html";

        File file = new File(STATIC_DIR + path);
        if (!file.exists() || file.isDirectory()) return false;

        String contentType = getContentType(path);
        byte[] content     = Files.readAllBytes(file.toPath());
        sendResponse(out, 200, contentType, content);
        return true;
    }

    private static String getContentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=UTF-8";
        if (path.endsWith(".css"))  return "text/css";
        if (path.endsWith(".js"))   return "application/javascript";
        if (path.endsWith(".png"))  return "image/png";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".ico"))  return "image/x-icon";
        return "application/octet-stream";
    }

    // Utilidades HTTP

    private static void sendResponse(OutputStream out, int status, String contentType, byte[] body) throws IOException {
        String statusText = status == 200 ? "OK" : String.valueOf(status);
        String header = "HTTP/1.1 " + status + " " + statusText + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(body);
    }

    private static void sendError(OutputStream out, int code, String message) throws IOException {
        byte[] body = ("<h1>" + code + " " + message + "</h1>").getBytes(StandardCharsets.UTF_8);
        sendResponse(out, code, "text/html; charset=UTF-8", body);
    }

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) return params;

        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                String key   = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                String value = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                params.put(key, value);
            } else if (kv.length == 1) {
                params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), "");
            }
        }
        return params;
    }
}