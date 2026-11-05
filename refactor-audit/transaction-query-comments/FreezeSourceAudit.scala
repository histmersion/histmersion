import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class FreezeSourceAudit extends SyntacticRule("FreezeSourceAudit") {
  private implicit val dialect: Dialect = dialects.Scala3
  private def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
  override def afterComplete(): Unit = {
    val out = Paths.get("refactor-audit/transaction-query-comments/source-holdout")
    require(!Files.exists(out)); Files.createDirectories(out)
    val selections = List(
      "automation/agents/LeaseRepo" -> List("claim","release"),
      "catalogue/events/EventRepo" -> List("put"),
      "library/labels/LabelRepo" -> List("facets"),
      "catalogue/periods/PeriodRepo" -> List("get","children"),
      "automation/agents/AgentStateRepo" -> List("set","delete"),
      "research/articles/CoverageRepo" -> List("sourceSet","members")
    )
    val frozen = Files.readAllLines(Paths.get("refactor-audit/models/pagination/build-v7/sources.tsv")).asScala.drop(1).map(_.split("\t")).map(a => a(0) -> a(1)).toMap
    val hits = Files.readAllLines(Paths.get("refactor-audit/assessment/current/capabilities/findings.tsv")).asScala.drop(1).map(_.split("\t",-1).toList).toList
    val evidence = scala.collection.mutable.ListBuffer.empty[String]
    val rows = selections.flatMap { case(file,names) =>
      val path = "modules/db/src/main/scala/com/histmersion/domain/" + file + ".scala"
      val source = Files.readString(Paths.get(path)); require(hash(source) == frozen(path))
      val tree = source.parse[Source].get
      names.map { name =>
        val defs = tree.collect { case d: Defn.Def if d.name.value == name => d }; require(defs.size == 1)
        val d = defs.head
        val owner = "com/histmersion/domain/" + file + "." + name + "()."
        val matches = hits.filter(r => Set("repo-transaction","repo-factory")(r.head) && (r(4) == owner || r(4).startsWith(owner)))
        val id = hash("transaction-source-holdout\t" + owner)
        def escape(s: String) = s.replace("\\","\\\\").replace("\t","\\t").replace("\n","\\n")
        evidence += List(id,owner,path,hash(source),d.syntax,d.structure).map(escape).mkString("\t")
        List(id,owner,"negative",matches.size.toString,if(matches.isEmpty) "true-negative" else "false-positive",path,(d.pos.startLine+1).toString).mkString("\t")
      }
    }
    require(rows.size == 10)
    Files.write(out.resolve("findings.tsv"),("id\towner\tindependent-label\tdiagnostics\tresult\tpath\tline" :: rows).asJava)
    Files.write(out.resolve("evidence.tsv"),("id\towner\tpath\tsource-sha256\tsource\tast" :: evidence.toList).asJava)
    val falsePositives = rows.count(_.split("\t",-1)(4) == "false-positive")
    Files.writeString(out.resolve("status.txt"),s"negative-methods=10\nfalse-positives=$falsePositives\npositive-methods=0\nprecision=undefined\nrecall=undefined\nSource hashes match the completed pre-repair project-wide detector observation. The entire method owner is checked, not only a declaration line. Parent deduplication turns twelve auditor snippets into ten methods: EventRepo.put and LabelRepo.facets were each sampled twice. Query SQL discovery is a separate supplemental positive, not part of this negative holdout.\n")
    require(falsePositives == 0)
  }
}
