import sbt.*

/** Centralised dependency versions (meta-build; imported by build.sbt).
  * Stack: SoftwareMill direct-style — Tapir + tapir-netty-server-sync, Chimp (MCP), Circe with
  * Kindlings derivation, Ox, Magnum (sync JDBC) + PostGIS, Flyway. Native-image via a custom task. */
object Dependencies {
  object V {
    val tapir     = "1.11.25"
    val sttp      = "4.0.3"    // sttp client4 (CLI → server HTTP client)
    val chimp     = "0.5.2"
    val circe     = "0.14.10"
    val kindlings = "0.3.1"    // com.kubuszok — Hearth-based circe / tapir-schema / sconfig derivation
    val decline   = "2.4.1"
    val ox        = "0.5.13"   // direct-style concurrency (wired when handlers need it)
    val magnum    = "1.3.0"    // sync JDBC
    val postgres  = "42.7.4"
    val flyway    = "11.8.2"   // schema versioning (SQL migrations under db/migration)
    val snakeyaml = "2.3"      // frontmatter parsing for the seed importer
    val sconfig   = "2.0.0"    // org.ekrich — HOCON config (Typesafe Config port, native-friendly)
    val flexmark  = "0.64.8"   // com.vladsch — Markdown → HTML rendering
    val scribe    = "3.15.0"   // com.outr — native-image-first Scala logging (+ scribe-slf4j bridge)
    val munit     = "1.0.4"
  }
}
