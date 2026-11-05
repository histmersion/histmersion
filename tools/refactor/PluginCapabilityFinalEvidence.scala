import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class PluginCapabilityFinalEvidence extends SyntacticRule("PluginCapabilityFinalEvidence") {
  override def afterComplete(): Unit = {
    implicit val dialect: Dialect = dialects.Scala3
    val dir = Paths.get("refactor-audit/plugin-capabilities")
    val rows = Files.readAllLines(dir.resolve("plan.tsv")).asScala.drop(1).map(_.split("\t", -1).toList).toList
    require(rows.size == 27)
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val corrections = scala.collection.mutable.ListBuffer.empty[String]
    val finalRows = rows.map { row =>
      val current = Files.readString(Paths.get(row.head))
      val original = Files.readString(Paths.get("target/plugin-capability-preview", row.head))
      require(hash(original) == row(2), "Frozen preview changed")
      val now = current.parse[Source].get
      val old = original.parse[Source].get
      if(hash(current) != row(2)) {
        val name = Paths.get(row.head).getFileName.toString
        name match {
          case "Subprocess.scala" => require(now.structure == old.structure, "Whitespace correction changed AST")
          case "SystemProcesses.scala" =>
            def capture(t: Tree) = t.collect { case d: Defn.Def if d.name.value == "capture" => d }.ensuring(_.size == 1).head
            val a = capture(now); val b = capture(old)
            val restored = current.patch(a.body.pos.start, b.body.syntax, a.body.pos.end - a.body.pos.start).parse[Source].get
            require(restored.structure == old.structure, "Unexpected process adapter correction")
            val attempts = a.body.collect { case t: Term.Apply if t.fun.syntax == "Try" => t }
            require(attempts.size == 1 && attempts.head.argClause.values.head.is[Term.Block], "Capture must retain the error-handling boundary around the complete operation")
          case "CalibreCollectionSuite.scala" =>
            val contexts = now.collect { case n: Term.NewAnonymous if n.templ.inits.exists(_.tpe.syntax == "Plugin.Context") => n }
            require(contexts.size == 1)
            val members = contexts.head.templ.body.stats.collect { case d: Defn.Def if Set("files", "processes")(d.name.value) => d }
            require(members.size == 2 && members.forall(_.body.syntax.startsWith("summon[")))
            val restored = members.sortBy(-_.pos.start).foldLeft(current)((text, d) => text.patch(d.pos.start, "", d.pos.end - d.pos.start)).parse[Source].get
            require(restored.structure == old.structure, "Test assertions or unrelated code changed")
          case _ => sys.error("Unrecorded post-preview change: " + row.head)
        }
        corrections += name
      }
      List(row.head, row(2), hash(current)).mkString("\t")
    }
    require(corrections.toSet == Set("Subprocess.scala", "SystemProcesses.scala", "CalibreCollectionSuite.scala"))
    Files.write(dir.resolve("final-plan.tsv"), ("path\tpreview-sha256\tfinal-sha256" :: finalRows).asJava)
    Files.write(dir.resolve("final-source-verification.txt"), List("PASS: all 27 applied files reconciled against the frozen preview",
      "Only three post-preview changes: whitespace-only Subprocess; capture error boundary; anonymous test context capability wiring.",
      "Test assertions and every unrelated AST remain unchanged.", "build-sha256=" + hash(Files.readString(Paths.get("build.sbt")))).asJava)
    println("PASS: frozen preview plus exactly the three documented corrections")
  }
}
