package com.example.puppeteer;

import com.rhett.rhettjs.extension.RhettJSExtension;
import com.rhett.rhettjs.extension.ScriptContext;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.Value;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Example RhettJS extension in Java (instead of Kotlin).
 *
 * Demonstrates:
 * - Java interop with RhettJS extension API
 * - Same functionality as Kotlin version
 * - How to work with GraalVM ProxyObject in Java
 */
public class PuppeteerExtensionJava {

    public static void init() {
        RhettJSExtension.INSTANCE.registerModule(
            new RhettJSExtension.ModuleConfig(
                // Module name
                "puppeteer",

                // API factory
                ctx -> new PuppeteerAPIJava(ctx).toProxyObject(),

                // Type definitions
                () -> Arrays.asList(
                    new scala.Tuple2<>("puppeteer.d.ts", loadResource("/types/puppeteer.d.ts")),
                    new scala.Tuple2<>("types.d.ts", loadResource("/types/types.d.ts"))
                ),

                // Available in these contexts
                new HashSet<>(Arrays.asList(ScriptContext.SERVER, ScriptContext.COMMAND)),

                // Priority
                0
            )
        );
    }

    private static String loadResource(String path) {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(
                PuppeteerExtensionJava.class.getResourceAsStream(path)
            )
        )) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            throw new RuntimeException("Resource not found: " + path, e);
        }
    }
}

/**
 * Puppeteer JavaScript API implementation (Java version).
 */
class PuppeteerAPIJava {

    private final RhettJSExtension.ExtensionContext ctx;

    public PuppeteerAPIJava(RhettJSExtension.ExtensionContext ctx) {
        this.ctx = ctx;
    }

    public ProxyObject toProxyObject() {
        Map<String, Object> api = new HashMap<>();

        // spawn(pos, options?) -> Promise<Puppet>
        api.put("spawn", (ProxyExecutable) args -> {
            // Parse position
            Value posObj = args[0];
            double x = posObj.getMember("x").asDouble();
            double y = posObj.getMember("y").asDouble();
            double z = posObj.getMember("z").asDouble();

            // Parse options (if provided)
            String name = "Puppet";
            if (args.length > 1 && !args[1].isNull() && args[1].hasMember("name")) {
                name = args[1].getMember("name").asString();
            }

            // In real implementation, spawn entity async
            java.util.concurrent.CompletableFuture<Map<String, Object>> future =
                java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    Map<String, Object> puppet = new HashMap<>();
                    puppet.put("id", UUID.randomUUID().toString());
                    puppet.put("name", name);

                    Map<String, Object> position = new HashMap<>();
                    position.put("x", x);
                    position.put("y", y);
                    position.put("z", z);
                    puppet.put("position", ProxyObject.fromMap(position));

                    return puppet;
                });

            // Convert to Promise
            return ctx.getBuiltins().getConvertFutureToPromise().apply(
                ctx.getGraalContext(),
                future.thenApply(ProxyObject::fromMap)
            );
        });

        // list() -> Puppet[]
        api.put("list", (ProxyExecutable) args -> {
            // Return empty list for example
            return Collections.emptyList();
        });

        // debugMode: boolean
        api.put("debugMode", ctx.getConfig().getDebug());

        return ProxyObject.fromMap(api);
    }
}
