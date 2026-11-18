import scalafix.v1._
import java.nio.file.{Files,Paths}
import java.nio.charset.StandardCharsets.UTF_8
import scala.jdk.CollectionConverters._

class ModelInventoryVerification extends SyntacticRule("ModelInventoryVerification") {
  override def afterComplete(): Unit = {
    val report = Paths.get("refactor-audit/models/verification.txt")
    Files.createDirectories(report.getParent); Files.writeString(report,"RUNNING\n")
    val logs = Paths.get("target/model-verification"); Files.createDirectories(logs)
    def run(label: String, args: List[String], expected: Int = 0): Unit = {
      println("Model inventory verification: " + label)
      val code = new ProcessBuilder(args.asJava).redirectErrorStream(true).redirectOutput(logs.resolve(label + ".log").toFile).start().waitFor()
      require(code == expected,s"$label exited $code, expected $expected; see $logs/$label.log")
    }
    val source = "tools/refactor/model-fixture/src/main/scala"
    run("compile",List("scala-cli","compile",source,"--server=false","-S","3.10.0","--scalac-option","-source:future",
      "--scalac-option","-Xsemanticdb","--scalac-option","-sourceroot","--scalac-option",Paths.get("").toAbsolutePath.toString,
      "--dep","io.github.iltotore::iron:3.4.1","--compilation-output","target/model-fixture-classes"))
    val cli = List("bash","tools/refactor/scalafix.sh") // the build's Scalafix; the fixture names its own --scala-version
    val args = List("--scala-version","3.10.0","--sourceroot",".","--classpath","target/model-fixture-classes","--files",source)
    run("positive",cli ++ List("--config","tools/refactor/model-fixture/inspect.conf") ++ args)
    def rows(name: String) = Files.readAllLines(Paths.get("target/model-fixture-report",name),UTF_8).asScala.drop(1).map(_.split("\t",-1).toList).toList
    val positions = rows("positions.tsv").map(p => p.head -> p).toMap
    val actual = rows("leaves.tsv").filter(_(2) == "primitive").map { l =>
      val p = positions(l.head)
      List(if(p(8).isEmpty) "<anonymous>" else p(8),p(5),l(3)).mkString("|")
    }.sorted
    val expected = Files.readAllLines(Paths.get("tools/refactor/model-fixture/expected-primitives.tsv"),UTF_8).asScala.toList.sorted
    require(actual == expected,s"Primitive inventory differs: missing=${expected.diff(actual)} unexpected=${actual.diff(expected)}")
    val leaves = rows("leaves.tsv")
    val constraints = rows("newtype-constraints.tsv").map(r => r.head -> r(7)).toMap
    require(constraints("modelinventoryfixture/Models$package.Year.") == "pure","Iron Pure not resolved")
    require(constraints("modelinventoryfixture/StrictCount.") == "non-Pure-constraint","Known non-Pure constraint missed")
    require(constraints("modelinventoryfixture/AliasedCount.") == "pure","Cross-file Pure alias not resolved")
    require(constraints("modelinventoryfixture/Lookalike.") != "pure","Unrelated Pure accepted")
    require(constraints("modelinventoryfixture/ExportedPure.") == "pure","Iron exported Pure missed")
    List("PositiveCount","NonnegativeCount","Latitude","Code","Title","Nonblank").foreach { name =>
      require(constraints("modelinventoryfixture/" + name + ".") == "non-Pure-constraint","Known constraint missed: " + name)
    }
    require(rows("newtype-constraints.tsv").exists(r => r.head == "modelinventoryfixture/StrictCount." && r(9) == "client-constraint-review"),"Configured client classification missed")
    List("existingId","existingYear","externalId","externalYear").foreach { name =>
      require(leaves.exists(l => positions(l.head)(8) == name && l(2) == "newtype"),"Missing existing newtype: " + name)
    }
    require(positions.values.exists(p => p(8) == "localCount" && p(3) == "production/local"),"Local scope lost")
    val roles = rows("position-roles.tsv").map(r => r.head -> r(1)).toMap
    def hasRole(name: String, kind: String, role: String): Boolean = positions.values.exists(p => p(8) == name && p(5) == kind && roles(p.head) == role)
    require(hasRole("title","constructor-parameter","record-field"),"Record field role lost")
    require(hasRole("pageSize","parameter","trait-contract"),"Public trait contract role lost")
    require(hasRole("localCount","value","local-position"),"Local variable promoted to model contract")
    require(hasRole("rawId","parameter","configured-boundary"),"Configured parse boundary lost")
    require(hasRole("rawCount","parameter","non-public-member-review"),"Private helper parameter promoted to public contract")
    val parameterization = rows("parameterization.tsv")
    require(parameterization.exists(r => r.head == "modelinventoryfixture/PageSize#" && r(2).isEmpty),"PageSize phantom candidate lost")
    require(parameterization.exists(r => r.head == "modelinventoryfixture/Page#" && r(2).nonEmpty),"Page representation use lost")
    require(rows("uses.tsv").exists(r => r.head == "modelinventoryfixture/Record#title." && r(3) == "modelinventoryfixture/Operations.lookup()."),"Resolved consumer edge lost")
    run("negative",cli ++ List("--config","tools/refactor/model-fixture/reject.conf") ++ args,1)
    run("absent-boundary",cli ++ List("--config","tools/refactor/model-fixture/reject-boundary.conf") ++ args,1)
    run("coverage-rejection",cli ++ List("--config","tools/refactor/model-fixture/inspect.conf") ++ args.dropRight(1) ++ List(source + "/Models.scala"),1)
    Files.write(report,List("PASS",s"${expected.size} exact primitive occurrences", "opaque/Iron and cross-file alias classification", "shadowed primitive names excluded", "nested, inferred, union, by-name, repeated and singleton types", "phantom-vs-represented type parameter evidence", "resolved consumer relationship", "negative assertion and missing-file coverage rejected").asJava,UTF_8)
  }
}
