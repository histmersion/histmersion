import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class VerifyMeasurementSelfTest extends SyntacticRule("VerifyMeasurementSelfTest") {
  override def afterComplete(): Unit = {
    val root = Paths.get("refactor-audit/models/pagination/review-history/self-test-1")
    require(!Files.exists(root)); Files.createDirectories(root)
    val actual = Paths.get("refactor-audit/models/pagination/review-history/after-pagination-1/findings.tsv")
    val lines = Files.readAllLines(actual).asScala.toList
    val rows = lines.tail.map(_.split("\t",-1).toList)
    val fixed = rows.find(_(1) == "verified-fixed").get.head
    val outstanding = rows.find(_(1) == "outstanding").get.head
    val substituted = rows.map { r =>
      if(r.head == fixed) r.updated(1,"outstanding")
      else if(r.head == outstanding) r.updated(1,"verified-fixed")
      else r
    }
    require(rows.count(_(1) == "verified-fixed") == substituted.count(_(1) == "verified-fixed"))
    val fake = root.resolve("same-count-different-identities.tsv")
    Files.write(fake,(lines.head :: substituted.map(_.mkString("\t"))).asJava)
    val cli = List("scala-cli","run","--server=false","-q","-S","2.13.18","--dep","ch.epfl.scala:scalafix-cli_2.13.18:0.14.9","--main-class","scalafix.cli.Cli","--")
    List(("unchanged",actual,0,"PASS"),("substituted",fake,1,"REGRESSION")).foreach { case(name,previous,expectedExit,expectedStatus) =>
      val report = root.resolve(name)
      val conf = root.resolve(name + ".conf")
      Files.writeString(conf,"rules = [\"file:refactor-audit/models/pagination/VerifyMeasurement.scala\"]\nVerifyMeasurement {\n report = \"" + report + "\"\n previous = \"" + previous + "\"\n}\n")
      val args = cli ++ List("--config",conf.toString,"--files","refactor-audit/models/pagination/VerifyMeasurement.scala")
      val code = new ProcessBuilder(args.asJava).redirectErrorStream(true).redirectOutput(root.resolve(name + ".log").toFile).start().waitFor()
      require(code == expectedExit,"Unexpected verifier exit: " + name)
      val status = Files.readAllLines(report.resolve("status.txt")).asScala.toList
      require(status.head == expectedStatus)
      require(status.contains("reopened=" + (if(name == "unchanged") "0" else "1")))
    }
    Files.writeString(root.resolve("status.txt"),"PASS: unchanged identities accepted; equal aggregate fixed count with one reopened identity rejected. Production sources and original review/baseline were not modified.\n")
    println(Files.readString(root.resolve("status.txt")))
  }
}
