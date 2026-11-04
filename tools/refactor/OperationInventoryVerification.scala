import scalafix.v1._
import java.nio.file.{Files,Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._

class OperationInventoryVerification extends SyntacticRule("OperationInventoryVerification") {
  override def afterComplete(): Unit = {
    val report = Paths.get("refactor-audit/operations/verification.txt")
    Files.createDirectories(report.getParent); Files.writeString(report,"RUNNING\n")
    val logs = Paths.get("target/operation-verification"); Files.createDirectories(logs)
    def run(label: String, args: List[String], expected: Int = 0): Unit = {
      println("Operation inventory verification: " + label)
      val code = new ProcessBuilder(args.asJava).redirectErrorStream(true).redirectOutput(logs.resolve(label + ".log").toFile).start().waitFor()
      require(code == expected,s"$label exited $code, expected $expected; see $logs/$label.log")
    }
    val source = "tools/refactor/operation-fixture/src/main/scala"
    run("compile",List("scala-cli","compile",source,"--server=false","-S","3.10.0","--scalac-option","-source:future",
      "--scalac-option","-Xsemanticdb","--scalac-option","-sourceroot","--scalac-option",Paths.get("").toAbsolutePath.toString,
      "--dep","io.github.iltotore::iron:3.4.1","--dep","com.kubuszok::kindlings-tapir-schema-derivation:0.3.3",
      "--dep","com.kubuszok::kindlings-circe-derivation:0.3.3","--compilation-output","target/operation-fixture-classes"))
    val cli = List("scala-cli","run","--server=false","-q","-S","2.13.18","--dep","ch.epfl.scala:scalafix-cli_2.13.18:0.14.9","--main-class","scalafix.cli.Cli","--")
    val args = List("--scala-version","3.10.0","--sourceroot",".","--classpath","target/operation-fixture-classes","--files",source)
    run("positive",cli ++ List("--config","tools/refactor/operation-fixture/inspect.conf") ++ args)
    val rows = Files.readAllLines(Paths.get("target/operation-fixture-report/occurrences.tsv"),UTF_8).asScala.drop(1).map(_.split("\t",-1).toList).toList
    val originalRows = rows.filterNot(_(2).endsWith("/Duplicates.scala"))
    val counts = originalRows.groupBy(r => (r.head,r(1))).view.mapValues(_.size).toMap
    val expected = Map(
      ("copy","case-class-copy") -> 3, ("copy","non-case-class-copy") -> 1,
      ("fold","visible-allocation-review") -> 3, ("fold","no-visible-allocation; indirect-effects-unresolved") -> 2,
      ("unwrap","project-value-member-review") -> 3, ("unwrap","iron-accessor") -> 1,
      ("given-instance","instance-body-review") -> 4,
      ("derivation-call","kindlings-derived") -> 1, ("derivation-call","other-derived-review") -> 1)
    require(counts == expected,s"Operation classifications differ: $counts")
    val duplicates = rows.filter(_(2).endsWith("/Duplicates.scala"))
    require(duplicates.size == 2 && duplicates.forall(r => r.head == "copy" && r(1) == "case-class-copy"),"Same-line copies must each be counted")
    require(duplicates.map(_(4)).distinct.size == 1 && duplicates.map(_(10)).distinct.size == 1 && duplicates.map(_(13)).distinct.size == 2,"Identical same-line expressions require distinct source offsets")
    require(rows.exists(r => r.head == "fold" && r(5).endsWith("Examples.seedOnly().") && r(1).startsWith("no-visible")),"One-time seed allocation counted as callback allocation")
    require(rows.exists(r => r.head == "fold" && r(5).endsWith("Examples.constructing().") && r(1) == "visible-allocation-review"),"Case-class apply in callback missed")
    run("negative",cli ++ List("--config","tools/refactor/operation-fixture/reject.conf") ++ args,1)
    require(rows.exists(r => r.head == "given-instance" && r(6).contains("textInt")),"Template-style given missed")
    require(rows.exists(r => r.head == "copy" && r(5).endsWith("Examples.returned().") && r(1) == "case-class-copy"),"Method-result copy missed")
    Files.write(report,List("PASS","19 original classified occurrences plus 2 identical same-line copies","distinct source offsets preserve separate occurrences","case-class and unrelated copies distinguished","fold callback allocations separated from one-time seed","Iron accessor separated from ordinary value members","Kindlings derivation and unrelated derived method distinguished","Alias and template-style givens inventoried","false classification rejected").asJava,UTF_8)
  }
}
