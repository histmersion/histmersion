import scalafix.v1._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

object BatchTestVerification {
  case class Policy(phase: String = "baseline", module: String = "server", suites: List[String] = Nil, report: String = "refactor-audit/agent-storage/tests", knownFailure: String = "")
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
}
class BatchTestVerification(val policy: BatchTestVerification.Policy) extends SyntacticRule("BatchTestVerification") {
  def this() = this(BatchTestVerification.Policy())
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("BatchTestVerification")(BatchTestVerification.Policy()).map(new BatchTestVerification(_))
  override def afterComplete(): Unit = {
    require(Set("baseline", "after")(policy.phase) && policy.suites.nonEmpty)
    val out = Paths.get(policy.report); Files.createDirectories(out)
    val snapshot = out.resolve(policy.phase + ".tsv")
    require(policy.phase != "baseline" || !Files.exists(snapshot), "Never overwrite the test baseline")
    val log = Paths.get("target/batch-test-verification", out.getParent.getFileName.toString, policy.phase + ".log")
    Files.createDirectories(log.getParent)
    val start = System.currentTimeMillis()
    val command = s"${policy.module}/testOnly ${policy.suites.map("*" + _).mkString(" ")}"
    val args = List("sbt", "--server", "-batch", "set every scalafmtOnCompile := false", "set ThisBuild / scalacOptions += \"-Xsemanticdb\"", command)
    val exit = new ProcessBuilder(args.asJava).redirectErrorStream(true).redirectOutput(log.toFile).start().waitFor()
    val stream = Files.walk(Paths.get("target/out"))
    val reports = try stream.iterator.asScala.filter(p => p.toString.endsWith(".xml") && p.getParent.getFileName.toString == "test-reports" &&
      p.getParent.getParent.getFileName.toString == "histmersion-" + policy.module).toList finally stream.close()
    val factory = DocumentBuilderFactory.newInstance(); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    val parser = factory.newDocumentBuilder()
    val seen = scala.collection.mutable.Set.empty[String]
    def escaped(s: String): String = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")
    val rows = reports.flatMap { path =>
      val cases = parser.parse(path.toFile).getElementsByTagName("testcase")
      (0 until cases.getLength).flatMap { i =>
        val t = cases.item(i).asInstanceOf[Element]; val clazz = t.getAttribute("classname"); val simple = clazz.split('.').last
        Option.when(policy.suites.contains(simple)) {
          require(Files.getLastModifiedTime(path).toMillis >= start, "Stale test report: " + path)
          seen += simple
          val status = List("failure", "error", "skipped").find(t.getElementsByTagName(_).getLength > 0).getOrElse("passed")
          val message = if(status == "passed") "" else t.getElementsByTagName(status).item(0).getTextContent
          List(clazz, t.getAttribute("name"), status, message).map(escaped)
        }
      }
    }.sortBy(_.take(2).mkString("\t"))
    require(seen.toSet == policy.suites.toSet && rows.nonEmpty, "Missing selected suite identities: " + (policy.suites.toSet -- seen))
    require(rows.map(_.take(2)).distinct.size == rows.size, "Duplicate test identities")
    Files.write(snapshot, ("class\ttest\tstatus\tmessage" :: rows.map(_.mkString("\t"))).asJava)
    val summary = List(s"tests=${rows.size}", s"failures=${rows.count(r => Set("failure", "error")(r(2)))}", s"skipped=${rows.count(_(2) == "skipped")}", s"exit=$exit")
    Files.write(out.resolve(policy.phase + ".txt"), summary.asJava)
    println(summary.mkString("\n"))
    if(policy.phase == "after") {
      val before = Files.readAllLines(out.resolve("baseline.tsv")).asScala.drop(1).map(_.split("\t", -1).toList).map(r => r.take(2) -> r).toMap
      require(before.keySet == rows.map(_.take(2)).toSet, "Test identity coverage changed")
      require(rows.forall(r => r(2) == "passed" || before(r.take(2))(2) == r(2)), "New test failure or skip")
      def failureEvidence(message: String): String = {
        val marker = "files in plugin/ the generator does not write\\n=> Obtained\\n"
        val at = message.indexOf(marker)
        require(at >= 0, "Known failure changed its assertion")
        message.substring(at).split("\\\\n").takeWhile(line => !line.startsWith("\\t") && !line.trim.startsWith("at ")).mkString("\n").trim
      }
      rows.filter(_(2) != "passed").foreach { row =>
        require(row.take(2).mkString("::") == policy.knownFailure, "Unreviewed failure")
        require(failureEvidence(row(3)) == failureEvidence(before(row.take(2))(3)), "Existing failure evidence changed")
      }
    }
    require(rows.forall(r => r(2) == "passed" || r(2) == "failure" && r.take(2).mkString("::") == policy.knownFailure), "Unexpected failure or skip")
    require(exit == (if(rows.forall(_(2) == "passed")) 0 else 1), "Unexpected test process exit")
    println(if(exit == 0) "ALL_SELECTED_TESTS_PASS" else "RETAINED_KNOWN_FAILURE; not an all-green test run")
  }
}
