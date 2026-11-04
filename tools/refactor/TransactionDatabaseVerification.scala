import scalafix.v1._
import metaconfig.{ConfDecoder,Configured,generic}
import java.nio.file.{Files,Paths,Path}
import scala.jdk.CollectionConverters._
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

object TransactionDatabaseVerification {
  case class Policy(phase: String = "baseline")
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
}
class TransactionDatabaseVerification(val policy: TransactionDatabaseVerification.Policy) extends SyntacticRule("TransactionDatabaseVerification") {
  def this() = this(TransactionDatabaseVerification.Policy())
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("TransactionDatabaseVerification")(TransactionDatabaseVerification.Policy()).map(new TransactionDatabaseVerification(_))
  override def afterComplete(): Unit = {
    require(Set("baseline","after")(policy.phase))
    val out = Paths.get("refactor-audit/transaction-repair/database")
    Files.createDirectories(out)
    val snapshot = out.resolve(policy.phase + ".tsv")
    require(policy.phase != "baseline" || !Files.exists(snapshot),"Frozen database baseline already exists")
    val start = System.currentTimeMillis()
    val db = if(policy.phase == "baseline") "*QueryRepoSuite *GazetteerRepoSuite *ImportedPlaceTimeSuite" else "*QueryRepoSuite"
    val server = if(policy.phase == "baseline") "*VacuumHttpSuite *GazetteerLoadSuite" else "*VacuumHttpSuite *GazetteerLoadSuite *GazetteerRepoSuite *ImportedPlaceTimeSuite"
    val logs = Paths.get("target/transaction-database-verification",policy.phase); Files.createDirectories(logs)
    val exits = List("db" -> db,"server" -> server).map { case (module,suites) =>
      val settings = if(policy.phase == "after") List("set ThisBuild / scalacOptions += \"-Xsemanticdb\"") else Nil
      val process = new ProcessBuilder((List("sbt","--server","-batch","set every scalafmtOnCompile := false") ++ settings :+ s"$module/testOnly $suites").asJava)
      process.environment().put("HISTMERSION_TEST_DB_URL","jdbc:postgresql://127.0.0.1:55573/histmersion_test")
      process.environment().put("HISTMERSION_TEST_FORKS","1")
      process.environment().put("HISTMERSION_PG_BIN_DIR",Paths.get("runtime/postgres/.pixi/envs/default/bin").toAbsolutePath.toString)
      val exit = process.redirectErrorStream(true).redirectOutput(logs.resolve(module + ".log").toFile).start().waitFor()
      println(s"$module ${policy.phase} test exit=$exit")
      module -> exit
    }
    val suiteModules = Map("QueryRepoSuite" -> "histmersion-db","VacuumHttpSuite" -> "histmersion-server","GazetteerLoadSuite" -> "histmersion-server",
      "GazetteerRepoSuite" -> (if(policy.phase == "baseline") "histmersion-db" else "histmersion-server"),
      "ImportedPlaceTimeSuite" -> (if(policy.phase == "baseline") "histmersion-db" else "histmersion-server"))
    val stream = Files.walk(Paths.get("target/out"))
    val reports = try stream.iterator.asScala.filter(p => p.toString.endsWith(".xml") && p.getParent.getFileName.toString == "test-reports").toList finally stream.close()
    val factory = DocumentBuilderFactory.newInstance(); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true)
    val parser = factory.newDocumentBuilder()
    val seen = scala.collection.mutable.Set.empty[String]
    def escape(s: String): String = s.replace("\\","\\\\").replace("\t","\\t").replace("\n","\\n").replace("\r","\\r")
    val rows = reports.flatMap { path =>
      val cases = parser.parse(path.toFile).getElementsByTagName("testcase")
      (0 until cases.getLength).flatMap { i =>
        val t = cases.item(i).asInstanceOf[Element]; val clazz = t.getAttribute("classname"); val simple = clazz.split('.').last
        suiteModules.get(simple).filter(module => path.getParent.getParent.getFileName.toString == module).map { _ =>
          require(Files.getLastModifiedTime(path).toMillis >= start,"Stale selected test report: " + path)
          seen += simple
          val status = List("failure","error","skipped").find(t.getElementsByTagName(_).getLength > 0).getOrElse("passed")
          val message = if(status == "passed") "" else t.getElementsByTagName(status).item(0).getTextContent
          List(clazz,t.getAttribute("name"),status,message).map(escape)
        }
      }
    }.sortBy(_.take(2).mkString("\t"))
    require(seen.toSet == suiteModules.keySet,"Selected suites missing: " + (suiteModules.keySet -- seen))
    require(rows.map(_.take(2)).distinct.size == rows.size,"Duplicate test identity")
    Files.write(snapshot,("class\ttest\tstatus\tmessage" :: rows.map(_.mkString("\t"))).asJava)
    val summary = List(s"tests=${rows.size}",s"failures=${rows.count(r => Set("failure","error")(r(2)))}",s"skipped=${rows.count(_(2) == "skipped")}") ++ exits.map { case (m,c) => s"$m-exit=$c" }
    Files.write(out.resolve(policy.phase + ".txt"),summary.asJava)
    println(summary.mkString("\n"))
    if(policy.phase == "after") {
      val before = Files.readAllLines(out.resolve("baseline.tsv")).asScala.drop(1).map(_.split("\t",-1).toList).map(r => r.take(2) -> r).toMap
      require(before.keySet == rows.map(_.take(2)).toSet,"Test identities changed beyond the approved module moves")
      require(rows.forall(r => r(2) == "passed" || before(r.take(2))(2) == r(2)),"New failures or skipped tests")
    }
    require(rows.forall(_(2) != "skipped"),"Database suites skipped; not validation")
  }
}
