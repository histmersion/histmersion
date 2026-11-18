import sbt.*

/** Centralised dependency versions (meta-build; imported by build.sbt). Stack: SoftwareMill direct-style — Tapir +
  * tapir-netty-server-sync, Chimp (MCP), jsoniter-scala with Kindlings derivation, Ox, Magnum (sync JDBC) + PostGIS,
  * Flyway. Native-image via a custom task.
  */
object Dependencies {
  object V {
    val tapir = "1.14.1" // built with Scala 3.9: needs the 3.10 compiler the build uses
    val sttp = "4.1.3" // sttp client4 (CLI → server HTTP client); what tapir 1.14.1 is built with
    // io.netty — tapir-netty-server-sync brings netty-all 4.2.19; overridden build-wide to the newest 4.2 patch for its
    // security fixes (4.2.20: two out-of-bounds reads in netty-buffer, among others). Drop when tapir brings it.
    val netty = "4.2.21.Final"
    val chimp = "0.6.0"
    val jsoniter =
      "2.43.0" // com.github.plokhotnyuk.jsoniter-scala — the JSON codecs (Kindlings 0.4.0 is built on 2.42.3)
    val kindlings =
      "0.4.0" // com.kubuszok — Hearth-based jsoniter / tapir-schema / sconfig derivation
    val hearth =
      "0.4.5" // com.kubuszok — the macro commons Kindlings 0.4.0 is built on (its version; hearth-cross-quotes follows it)
    val scallop =
      "6.0.1" // org.rogach — the CLI parser (no dependency but the Scala library)
    val ox = "1.0.2" // direct-style concurrency (via tapir-netty-server-sync); 1.0+ dropped
    // StructuredTaskScope for its own ThreadHerd → JDK 25 (JEP 505) safe
    val magnum = "1.3.1" // sync JDBC
    val postgres = "42.7.14"
    val hikari = "7.1.0" // com.zaxxer — JDBC connection pool (native-image-safe, no reflection)
    val flyway = "13.12.2" // schema versioning (SQL migrations under db/migration); 11.20+ knows PostgreSQL 18
    val snakeyaml = "2.7" // frontmatter parsing for the seed importer
    val sconfig = "2.1.0" // org.ekrich — HOCON config (Typesafe Config port, native-friendly)
    val flexmark = "0.64.8" // com.vladsch — Markdown → HTML rendering
    val scribe = "3.19.1" // com.outr — native-image-first Scala logging (+ scribe-slf4j bridge)
    val laminar = "17.2.1" // com.raquo — reactive UI for Scala.js
    val sttpAi = "0.12.0" // com.softwaremill.sttp.ai — LLM provider clients + agent loop (embedded
    // agents, docs/research-embedded-agents.md); same tapir/sttp as above
    val chimney = "2.1.1" // io.scalaland — layer-to-layer model conversions (chimney-iron extends its macros)
    val iron = "3.4.1" // io.github.iltotore — refined newtypes
    val munit = "1.3.6"
    val apispec = "0.12.0" // com.softwaremill.sttp.apispec — what tapir 1.14 and sttp-ai 0.12 bring
    val coursierInterface = "1.0.30" // io.get-coursier — coursier's Java API, one shaded jar: the plugin resolver
    val scalaJavaTime = "2.11.0" // io.github.cquiroz — java.time for Scala.js (Queue, Backup)
  }
}
