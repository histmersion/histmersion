import sbt.*

/** Centralised dependency versions (meta-build; imported by build.sbt).
  * Stack: SoftwareMill direct-style — Tapir + tapir-netty-server-sync, Chimp (MCP), Circe with
  * Kindlings derivation, Ox, Magnum (sync JDBC) + PostGIS, Flyway. Native-image via a custom task. */
object Dependencies {
  object V {
    val tapir     = "1.13.31"
    val sttp      = "4.0.26"   // sttp client4 (CLI → server HTTP client); matches chimp 0.5.2 / tapir 1.13.31
    val chimp     = "0.5.2"
    val circe     = "0.14.10"
    val kindlings = "0.3.1"    // com.kubuszok — Hearth-based circe / tapir-schema / sconfig derivation
    val decline   = "2.4.1"
    val ox        = "1.0.2"    // direct-style concurrency (via tapir-netty-server-sync); 1.0+ dropped
                               // StructuredTaskScope for its own ThreadHerd → JDK 25 (JEP 505) safe
    val magnum    = "1.3.0"    // sync JDBC
    val postgres  = "42.7.4"
    val hikari    = "5.1.0"    // com.zaxxer — JDBC connection pool (native-image-safe, no reflection)
    val flyway    = "13.11.1"  // schema versioning (SQL migrations under db/migration); 11.20+ knows PostgreSQL 18
    val snakeyaml = "2.3"      // frontmatter parsing for the seed importer
    val sconfig   = "2.0.0"    // org.ekrich — HOCON config (Typesafe Config port, native-friendly)
    val flexmark  = "0.64.8"   // com.vladsch — Markdown → HTML rendering
    val scribe    = "3.15.0"   // com.outr — native-image-first Scala logging (+ scribe-slf4j bridge)
    val laminar       = "17.2.1"  // com.raquo — reactive UI for Scala.js
    val sttpAi        = "0.11.4"  // com.softwaremill.sttp.ai — LLM provider clients + agent loop (embedded
                                 // agents, docs/research-embedded-agents.md); same tapir/sttp as above
    val munit         = "1.0.4"
    val scalaJavaTime = "2.6.0"  // io.github.cquiroz — java.time for Scala.js (Queue, Backup)
  }
}
