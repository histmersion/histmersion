import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class PaginationWebAssertions extends SyntacticRule("PaginationWebAssertions") {
  private val path = "modules/web/src/test/scala/com/histmersion/web/FilterTabSuite.scala"
  private val report = Paths.get("refactor-audit/models/pagination/web-assertions-v1")
  private var visited = false
  private def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
  override def fix(implicit doc: SyntacticDocument): Patch = {
    val input = doc.input match { case Input.File(p,_) => p.toNIO; case Input.VirtualFile(p,_) => Paths.get(p) }
    if(Paths.get("").toAbsolutePath.relativize(input.toAbsolutePath).toString != path) return Patch.empty
    require(!visited && !Files.exists(report)); visited = true
    val frozen = Files.readAllLines(Paths.get("refactor-audit/models/pagination/web-tests-v1/sources.tsv")).asScala.drop(1).map(_.split("\t")).map(a => a(0) -> a(1)).toMap
    require(hash(doc.input.text) == frozen(path), "Assertions changed since failing compilation")
    val assertions = doc.tree.collect { case a: Term.Apply if Set("assert", "assertEquals")(a.fun.syntax) => a }
    val matches = assertions.flatMap(_.argClause.values.head.collect { case s: Term.Select if s.name.value == "shown" => s })
    require(matches.size == 5)
    require(doc.tree.collect { case s: Term.Select if s.name.value == "shown" => s }.size == matches.size)
    Files.createDirectories(report)
    Files.writeString(report.resolve("before.scala"),doc.input.text)
    def escape(s: String) = s.replace("\\","\\\\").replace("\t","\\t").replace("\n","\\n")
    Files.write(report.resolve("shapes.tsv"),("line\tsource\tast\treplacement" :: matches.map(s => List((s.pos.startLine+1).toString,s.syntax,s.structure,s.syntax + ".value").map(escape).mkString("\t"))).asJava)
    matches.map(s => Patch.addRight(s,".value")).asPatch.atomic
  }
  override def afterComplete(): Unit = {
    require(visited)
    val next = Files.readString(Paths.get(path))
    Files.writeString(report.resolve("after.scala"),next)
    Files.writeString(report.resolve("status.txt"),"Five observed count expressions unwrapped; expected values, test names and assertion count unchanged. Runtime verification pending.\n")
  }
}

