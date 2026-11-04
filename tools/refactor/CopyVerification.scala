import scalafix.v1._
import scala.meta._
import java.nio.file.{Files,Paths}
import scala.jdk.CollectionConverters._

class CopyVerification extends SyntacticRule("CopyVerification") {
  override def afterComplete(): Unit = {
    implicit val dialect: Dialect = dialects.Scala3
    val logs = Paths.get("target/copy-verification"); Files.createDirectories(logs)
    def run(label: String, args: List[String], expected: Int = 0): Unit = {
      println("Copy verification: " + label)
      val code = new ProcessBuilder(args.asJava).redirectErrorStream(true).redirectOutput(logs.resolve(label + ".log").toFile).start().waitFor()
      require(code == expected,s"$label exited $code; see $logs/$label.log")
    }
    val base = "tools/refactor/copy-fixture/"
    val input = base + "input/src/main/scala"
    val output = "target/copy-fixture-output/src/main/scala"
    val flags = List("--server=false","-S","3.10.0","--scalac-option","-source:future","--dep","com.kubuszok::kindlings-optics:0.3.3",
      "--scalac-option","-Xsemanticdb","--scalac-option","-sourceroot","--scalac-option",Paths.get("").toAbsolutePath.toString)
    val cli = List("scala-cli","run","--server=false","-q","-S","2.13.18","--dep","ch.epfl.scala:scalafix-cli_2.13.18:0.14.9","--main-class","scalafix.cli.Cli","--")
    def fix(mode: String, path: String, cp: String, extra: List[String] = Nil, code: Int = 0): Unit =
      run(mode,cli ++ List("--config",base + mode + ".conf","--scala-version","3.10.0","--sourceroot",".","--classpath",cp,"--files",path) ++ extra,code)
    run("input",List("scala-cli","run",input,"--main-class","lensfixture.verify","--compilation-output","target/copy-fixture-classes") ++ flags)
    run("expected",List("scala-cli","run",base + "expected","--main-class","lensfixture.verify") ++ flags)
    fix("check",input,"target/copy-fixture-classes",code=64)
    fix("apply",input,"target/copy-fixture-classes",List("--out-from",input,"--out-to",output))
    val actual = Files.readString(Paths.get(output,"Example.scala"))
    val expected = Files.readString(Paths.get(base,"expected/Example.scala"))
    require(actual.parse[Source].get.structure == expected.parse[Source].get.structure,"Rewrite differs from expected AST")
    run("output",List("scala-cli","run",output,"--main-class","lensfixture.verify","--compilation-output","target/copy-fixture-output-classes") ++ flags)
    fix("check",output,"target/copy-fixture-output-classes")
    fix("apply",output,"target/copy-fixture-output-classes")
    require(actual == Files.readString(Paths.get(output,"Example.scala")),"Rewrite is not idempotent")
    Files.writeString(logs.resolve("verification.txt"),"PASS: expected AST, compiled behavior, evaluation order, negative/positive checks, deferred shapes, idempotence\n")
    val report = Paths.get("refactor-audit/copy-migration/verification.txt")
    Files.createDirectories(report.getParent)
    Files.writeString(report,Files.readString(logs.resolve("verification.txt")))
  }
}
