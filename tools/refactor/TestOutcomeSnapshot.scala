import scalafix.v1._
import metaconfig.{Configured,ConfDecoder,generic}
import java.nio.file.{Files,Paths}
import scala.jdk.CollectionConverters._
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

object TestOutcomeSnapshot {
  case class Policy(mode: String = "compare", report: String = "refactor-audit/copy-migration/test-outcomes")
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
}
class TestOutcomeSnapshot(val policy: TestOutcomeSnapshot.Policy) extends SyntacticRule("TestOutcomeSnapshot") {
  def this() = this(TestOutcomeSnapshot.Policy())
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("TestOutcomeSnapshot")(TestOutcomeSnapshot.Policy()).map(new TestOutcomeSnapshot(_))
  override def afterComplete(): Unit = {
    require(Set("capture","compare")(policy.mode))
    val factory = DocumentBuilderFactory.newInstance()
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true)
    val builder = factory.newDocumentBuilder()
    val stream = Files.walk(Paths.get("target/out"))
    val files = try stream.iterator.asScala.filter(p => p.toString.endsWith(".xml") && p.getParent.getFileName.toString == "test-reports").toList finally stream.close()
    require(files.nonEmpty,"No JUnit test reports")
    def esc(s: String) = s.replace("\\","\\\\").replace("\t","\\t").replace("\n","\\n").replace("\r","\\r")
    val rows = files.flatMap { path =>
      val cases = builder.parse(path.toFile).getElementsByTagName("testcase")
      (0 until cases.getLength).map { i =>
        val t = cases.item(i).asInstanceOf[Element]
        val status = List("failure","error","skipped").find(t.getElementsByTagName(_).getLength > 0).getOrElse("passed")
        val message = if(status == "passed") "" else t.getElementsByTagName(status).item(0).getTextContent
        List(path.getParent.getParent.toString,t.getAttribute("classname"),t.getAttribute("name"),status,message).map(esc)
      }
    }.sortBy(_.take(3).mkString("\t"))
    require(rows.map(_.take(3)).distinct.size == rows.size,"Duplicate test identities")
    val root = Paths.get(policy.report); Files.createDirectories(root)
    val baseline = root.resolve("baseline.tsv")
    val output = if(policy.mode == "capture") baseline else root.resolve("current.tsv")
    require(policy.mode != "capture" || !Files.exists(baseline),"Do not overwrite the frozen test baseline")
    Files.write(output,("module\tclass\ttest\tstatus\tmessage" :: rows.map(_.mkString("\t"))).asJava)
    val failures = rows.filter(r => Set("failure","error")(r(3)))
    val summary = s"${rows.size} test cases; ${failures.size} failures/errors; ${rows.count(_(3) == "skipped")} skipped"
    Files.writeString(root.resolve(policy.mode + ".txt"),summary + "\n")
    println(summary)
    if(policy.mode == "compare") {
      val old = Files.readAllLines(baseline).asScala.drop(1).map(_.split("\t",-1).toList).map(r => r.take(3) -> r).toMap
      val newlyFailed = failures.filter(r => !old.get(r.take(3)).exists(o => Set("failure","error")(o(3))))
      val changed = failures.filter(r => old.get(r.take(3)).exists(o => o(4) != r(4)))
      val newlySkipped = rows.filter(r => r(3) == "skipped" && old.get(r.take(3)).exists(_(3) != "skipped"))
      Files.write(root.resolve("new-skips.tsv"),newlySkipped.map(_.mkString("\t")).asJava)
      Files.write(root.resolve("changed-failure-summary.tsv"),changed.map(r => (r.take(3) ++ List(old(r.take(3))(4).take(500),r(4).take(500))).mkString("\t")).asJava)
      Files.write(root.resolve("changed-failures.tsv"),changed.map(_.mkString("\t")).asJava)
      Files.write(root.resolve("new-failures.tsv"),newlyFailed.map(_.mkString("\t")).asJava)
      require(old.keySet == rows.map(_.take(3)).toSet,"Test identity coverage changed")
      require(newlyFailed.isEmpty,"New test failures; inspect new-failures.tsv")
      require(newlySkipped.isEmpty,"Previously executed tests now skipped; inspect new-skips.tsv")
      println(s"No newly failing test identities; ${changed.size} existing failure messages need review. This does not mean all tests passed.")
    }
  }
}
