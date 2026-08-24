import sbt.*

/** Centralised dependency versions (meta-build; imported by build.sbt).
  * Stack: SoftwareMill direct-style — Tapir + tapir-netty-server-sync, Chimp (MCP), Circe with
  * Kindlings derivation, Ox, Magnum (sync JDBC) + PostGIS. Native-image via a custom task. */
object Dependencies {
  object V {
    val tapir    = "1.11.24"
    val chimp    = "0.5.2"
    val circe    = "0.14.10"
    val kindlings = "0.3.1"   // com.kubuszok — Hearth-based circe derivation
    val decline  = "2.4.1"
    val ox       = "0.5.13"   // direct-style concurrency (wired when handlers need it)
    val magnum   = "1.3.0"    // sync JDBC
    val postgres = "42.7.4"
    val snakeyaml = "2.3"     // frontmatter parsing for the seed importer
    val sconfig   = "2.0.0"   // org.ekrich — HOCON config (Typesafe Config port, native-friendly)
    val logback  = "1.5.12"
    val munit    = "1.0.4"
  }
}
