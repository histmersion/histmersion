# Native image

Both binaries are GraalVM `native-image` builds (~40–60 s each; the CLI binary is 50 MB, the server
binary 78 MB). The lessons that keep them working — the full list is in
[`AGENTS.md`](https://github.com/histmersion/histmersion/blob/master/AGENTS.md):

- **The JDK is pinned to GraalVM CE 25.0.1** (`.sdkmanrc` and CI together; `scripts/bundle/server.sh`
  refuses any other major).
  It was 22.0.2 until tapir 1.13.31: the older `tapir-netty-server-sync` pulled ox 0.5.x, whose
  `DoNothingScope extends StructuredTaskScope`, and JEP 505 made that an interface in JDK 25. ox 1.0.x
  uses its own `ThreadHerd` over virtual threads instead, so the preview API is gone from the stack.
- **Run the app once after adding a dependency.** `sbt server/run` / `sbt cli/run` fork under the
  native-image tracing agent and merge reachability metadata into
  `modules/<m>/src/main/resources/META-INF/native-image/reachability-metadata.json` (one file per
  module — the JDK-25 format; the legacy split `*-config.json` files are gone and must not come back).
  Commit the changed JSON. Skip this and you get a binary that builds cleanly and fails at runtime.
  Regenerate only with the pinned JDK, and exercise every `web/` asset plus an empty database, because
  resources are recorded one by one as they are requested.
- **Config is loaded eagerly, on the main thread** (GraalVM `StackValue`), and never through
  `ConfigFactory.load()` — that folds system properties into HOCON and dotted keys like
  `io.netty.allocator.type` collide.
- **netty**: unpooled allocator (the pooled one mishandles `ByteBuf` refCnt natively) and the pure-Java
  NIO transport (kqueue/epoll JNI transports excluded in `build.sbt`).
- **Logging is scribe**; `scribe-slf4j` bridges Flyway. No reflective logging backend.
- **Flyway** reads migrations through a generated `db/migration/index.txt`, because classpath scanning
  does not work in a native image. Adding a `V###__*.sql` file is enough.
- **sbt 2** puts module output at `target/out/jvm/<scalaVersion>/<name>/`, *not* `modules/<m>/target/`.

## The relink kit

A bundle carries `kit/`: the server's Sloth-patched jars in class-path order, the native-image flags
(`serverNativeOpts` in `build.sbt` is the one list) and their checksums. `bin/histmersion-relink`
links a server from it plus the plugin jars, with no sbt and no compile — that is what the installers
and `histmersion plugin rebuild` run. See [Installing plugins](../plugins/installing.md#relinking) and
{{ repo_file("docs/research-plugins.md") }} §18–§21.
