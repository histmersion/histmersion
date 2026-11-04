import scalafix.v1._
import metaconfig.{ConfDecoder,Configured,generic}
import java.nio.file.{Files,Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._
import com.typesafe.config.ConfigFactory

object InventoryCoverage {
  case class Evidence(path: String, column: String = "", matches: String = ".*")
  case class Goal(id: Int, evidence: List[Evidence], decision: String)
  case class Policy(goals: List[Goal] = Nil)
  object Evidence {
    implicit val surface: generic.Surface[Evidence] = generic.deriveSurface[Evidence]
    implicit val decoder: ConfDecoder[Evidence] = generic.deriveDecoder(Evidence("")).noTypos
  }
  object Goal {
    implicit val surface: generic.Surface[Goal] = generic.deriveSurface[Goal]
    implicit val decoder: ConfDecoder[Goal] = generic.deriveDecoder(Goal(0,Nil,"")).noTypos
  }
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
}
class InventoryCoverage(val policy: InventoryCoverage.Policy) extends SyntacticRule("InventoryCoverage") {
  def this() = this(InventoryCoverage.Policy())
  import InventoryCoverage._
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("InventoryCoverage")(Policy()).map(new InventoryCoverage(_))
  override def afterComplete(): Unit = {
    val out = Paths.get("refactor-audit/inventory"); Files.createDirectories(out); Files.writeString(out.resolve("status.txt"),"RUNNING\n")
    val goals = ConfigFactory.parseFile(Paths.get("tools/refactor/migration.conf").toFile).resolve().getConfigList("SourceContracts.goals").asScala.toList
    val titles = goals.map(g => g.getInt("id") -> g.getString("title")).toMap
    require(policy.goals.map(_.id).sorted == (1 to 43).toList && titles.keySet == (1 to 43).toSet,"Inventory must account for exactly all 43 goals")
    require(policy.goals.forall(_.decision.trim.nonEmpty),"Every goal needs an explicit remaining decision")
    val cache = scala.collection.mutable.Map.empty[String,List[List[String]]]
    def table(path: String): List[List[String]] = cache.getOrElseUpdate(path,
      Files.readAllLines(Paths.get(path),UTF_8).asScala.toList.map(_.split("\t",-1).toList))
    def count(e: Evidence): Int = {
      val rows = table(e.path)
      require(rows.nonEmpty,"Missing table header: " + e.path)
      val column = if(e.column.isEmpty) -1 else rows.head.indexOf(e.column)
      require(e.column.isEmpty || column >= 0,"Unknown evidence column: " + e)
      val pattern = e.matches.r
      rows.tail.count(r => pattern.findFirstIn(if(column < 0) r.mkString("\t") else r(column)).nonEmpty)
    }
    val families = List("models","operations","architecture","build","resources")
    families.foreach { f =>
      require(Files.readString(Paths.get("refactor-audit",f,"status.txt")).startsWith("COMPLETE"),"Incomplete evidence family: " + f)
    }
    val rows = policy.goals.sortBy(_.id).map { g =>
      val evidence = g.evidence.map(e => s"[${e.path.stripPrefix("refactor-audit/")}](${Paths.get(e.path).toAbsolutePath}) (${count(e)} rows" +
        (if(e.column.isEmpty) "" else s"; ${e.column} matches `${e.matches.replace("|","&#124;")}`") + ")")
      val status = if(g.evidence.isEmpty) "External evidence required" else "Evidence available; review required"
      s"| G${g.id}: ${titles(g.id)} | $status | ${evidence.mkString("<br>")} | ${g.decision} |"
    }
    Files.write(out.resolve("coverage.md"),(List("# Goal-by-goal inventory evidence","",
      "All 43 goals are accounted for below. Evidence coverage is not detector completeness or migration completion. Counts are candidate/evidence rows, overlap across goals, and must never be summed as violations. Zero selected rows requires investigation, not automatic approval.","",
      "The model and operation inventories distinguish unresolved symbols and legitimate look-alikes. Architecture inventories expose declarations and references for policy decisions; they do not infer business meaning, invariants or behavioral equivalence. Remaining decisions are explicit below. Source coverage and fixtures are checked by ProjectAssessment.","",
      "| Goal | Status | Evidence | Remaining decision or verification |","|---|---|---|---|") ++ rows).asJava,UTF_8)
    Files.writeString(out.resolve("status.txt"),"ALL_43_GOALS_INDEXED; POLICY_AND_EVIDENCE_GAPS_REMAIN\n")
  }
}
