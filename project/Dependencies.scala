import sbt.*

/** Centralised dependency versions (meta-build; imported by build.sbt). Stack: SoftwareMill direct-style — Tapir +
  * tapir-netty-server-sync, Chimp (MCP), jsoniter-scala with Kindlings derivation, Ox, Magnum (sync JDBC) + PostGIS,
  * Flyway. Native-image via a custom task.
  */
object Dependencies {
  object V {
    // THE Scala version: every module's compiler and library. Code outside sbt reads it from what `BuildVersions`
    // writes; .sdkmanrc's `scala=` must name the same one (BuildVersionsSuite).
    val scala = "3.10.1"
    // ch.epfl.scala scalafix-cli and the Scala 2.13 it is published for: tools only (GuidelineRatchetSuite and the
    // rules under tools/refactor read them from `BuildVersions`)
    val scalafix = "0.14.9"
    val scalafixScala = "2.13.18"
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
    val magnum = "2.0.0" // sync JDBC; 2.0 nests an interpolated Frag into sql"" and needs a DbCodec for every value
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

  /** The versions code outside sbt needs, in one `key=value` file: sbt writes it to `target/build-versions.properties`
    * whenever it loads the build (scripts and tools/refactor read it there) and into the server's test resources
    * (GuidelineRatchetSuite, BuildVersionsSuite). The JDK pin is not here: its source is .sdkmanrc and
    * scripts/bundle/graalvm-pins.txt, which scripts read directly.
    */
  object BuildVersions {
    val FileName = "build-versions.properties"

    val entries: List[(String, String)] =
      List("scala" -> V.scala, "scalafix" -> V.scalafix, "scalafixScala" -> V.scalafixScala)

    val text: String = entries.map { case (k, v) => s"$k=$v\n" }.mkString

    /** Writes `text` to `out` unless it holds it already: several sbt processes may load the same checkout. */
    def write(out: File): File = {
      if !out.isFile || IO.read(out) != text then {
        IO.createDirectory(out.getParentFile)
        val tmp = java.nio.file.Files.createTempFile(out.getParentFile.toPath, out.getName, ".tmp")
        IO.write(tmp.toFile, text)
        val _ = java.nio.file.Files.move(
          tmp,
          out.toPath,
          java.nio.file.StandardCopyOption.REPLACE_EXISTING,
          java.nio.file.StandardCopyOption.ATOMIC_MOVE
        )
      }
      out
    }

    /** A `key=value` line of .sdkmanrc (`java`, `sbt`, `scala`), the toolchain's pin. */
    def sdkmanrc(base: File, key: String): Option[String] = {
      val f = base / ".sdkmanrc"
      if !f.isFile then None
      else IO.readLines(f).map(_.trim).collectFirst { case l if l.startsWith(key + "=") => l.stripPrefix(key + "=") }
    }
  }
}
