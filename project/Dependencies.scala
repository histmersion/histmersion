import sbt.*

/** Centralised dependency versions (meta-build; imported by build.sbt).
  * Stack: SoftwareMill direct-style — Tapir + tapir-netty-server-sync, Chimp (MCP), Circe with
  * Kindlings derivation, Ox, Magnum (sync JDBC) + PostGIS, Flyway. Native-image via a custom task. */
object Dependencies {
  object V {
    val tapir     = "1.13.33"  // 1.14.x, sttp 4.1.x, sttp-ai 0.12.x, sconfig 2.1.x and scala-java-time 2.8.x
                               // are built with Scala 3.9 (scala3-library 3.9.0): they wait for the 3.9 move
    val sttp      = "4.0.27"   // sttp client4 (CLI → server HTTP client); matches tapir 1.13.33
    val chimp     = "0.6.0"
    val circe     = "0.14.17"
    val kindlings = "0.3.3"    // com.kubuszok — Hearth-based circe / tapir-schema / sconfig derivation
    val decline   = "2.6.2"
    val ox        = "1.0.2"    // direct-style concurrency (via tapir-netty-server-sync); 1.0+ dropped
                               // StructuredTaskScope for its own ThreadHerd → JDK 25 (JEP 505) safe
    val magnum    = "1.3.1"    // sync JDBC
    val postgres  = "42.7.14"
    val hikari    = "7.1.0"    // com.zaxxer — JDBC connection pool (native-image-safe, no reflection)
    val flyway    = "13.11.1"  // schema versioning (SQL migrations under db/migration); 11.20+ knows PostgreSQL 18
    val snakeyaml = "2.7"      // frontmatter parsing for the seed importer
    val sconfig   = "2.0.0"    // org.ekrich — HOCON config (Typesafe Config port, native-friendly)
    val flexmark  = "0.64.8"   // com.vladsch — Markdown → HTML rendering
    val scribe    = "3.19.0"   // com.outr — native-image-first Scala logging (+ scribe-slf4j bridge)
    val laminar       = "17.2.1"  // com.raquo — reactive UI for Scala.js
    val sttpAi        = "0.11.4"  // com.softwaremill.sttp.ai — LLM provider clients + agent loop (embedded
                                 // agents, docs/research-embedded-agents.md); same tapir/sttp as above
    val munit         = "1.3.6"
    val scalaJavaTime = "2.7.0"  // io.github.cquiroz — java.time for Scala.js (Queue, Backup)
  }
}
