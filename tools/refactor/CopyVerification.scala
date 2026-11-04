import scalafix.v1._
import scala.meta._
import java.nio.file.{Files,Paths}
import scala.jdk.CollectionConverters._
import metaconfig.{Configured,ConfDecoder,generic}

object CopyVerification {
  case class Policy(fixture: String = "tools/refactor/copy-fixture", output: String = "target/copy-fixture-output", report: String = "refactor-audit/copy-migration/verification.txt")
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
}
class CopyVerification(val policy: CopyVerification.Policy) extends SyntacticRule("CopyVerification") {
  def this() = this(CopyVerification.Policy())
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("CopyVerification")(CopyVerification.Policy()).map(new CopyVerification(_))
  override def afterComplete(): Unit = {
    implicit val dialect: Dialect = dialects.Scala3
    val logs = Paths.get(policy.output + "-verification"); Files.createDirectories(logs)
    def run(label: String, args: List[String], expected: Int = 0): Unit = {
      println("Copy verification: " + label)
      val code = new ProcessBuilder(args.asJava).redirectErrorStream(true).redirectOutput(logs.resolve(label + ".log").toFile).start().waitFor()
      require(code == expected,s"$label exited $code; see $logs/$label.log")
    }
    val base = policy.fixture + "/"
    val input = base + "input/src/main/scala"
    val output = policy.output + "/src/main/scala"
    val flags = List("--server=false","-S","3.10.0","--scalac-option","-source:future","--dep","com.kubuszok::kindlings-optics:0.3.3",
      "--scalac-option","-Xsemanticdb","--scalac-option","-sourceroot","--scalac-option",Paths.get("").toAbsolutePath.toString)
    val cli = List("scala-cli","run","--server=false","-q","-S","2.13.18","--dep","ch.epfl.scala:scalafix-cli_2.13.18:0.14.9","--main-class","scalafix.cli.Cli","--")
    def fix(mode: String, path: String, cp: String, extra: List[String] = Nil, code: Int = 0): Unit =
      run(mode,cli ++ List("--config",base + mode + ".conf","--scala-version","3.10.0","--sourceroot",".","--classpath",cp,"--files",path) ++ extra,code)
    run("input",List("scala-cli","run",input,"--main-class","lensfixture.verify","--compilation-output","target/copy-fixture-classes") ++ flags)
    run("expected",List("scala-cli","run",base + "expected","--main-class","lensfixture.verify") ++ flags)
    if(Files.exists(Paths.get(base,"index.conf"))) run("index",cli ++ List("--config",base + "index.conf","--scala-version","3.10.0","--sourceroot",".","--classpath","target/copy-fixture-classes","--files",input))
    fix("check",input,"target/copy-fixture-classes",code=64)
    fix("apply",input,"target/copy-fixture-classes",List("--out-from",input,"--out-to",output))
    def sources(directory: String): Map[String,String] = {
      val stream = Files.list(Paths.get(directory))
      try stream.iterator.asScala.filter(_.toString.endsWith(".scala")).map(p => p.getFileName.toString -> Files.readString(p)).toMap
      finally stream.close()
    }
    val actual = sources(output)
    val expected = sources(base + "expected")
    require(actual.keySet == expected.keySet,"Rewrite fixture files differ")
    actual.foreach { case(file,source) => require(source.parse[Source].get.structure == expected(file).parse[Source].get.structure,"Rewrite differs from expected AST: " + file) }
    run("output",List("scala-cli","run",output,"--main-class","lensfixture.verify","--compilation-output","target/copy-fixture-output-classes") ++ flags)
    fix("check",output,"target/copy-fixture-output-classes")
    fix("apply",output,"target/copy-fixture-output-classes")
    require(actual == sources(output),"Rewrite is not idempotent")
    Files.writeString(logs.resolve("verification.txt"),"PASS: expected AST, compiled behavior, evaluation order, negative/positive checks, deferred shapes, idempotence\n")
    val report = Paths.get(policy.report)
    Files.createDirectories(report.getParent)
    Files.writeString(report,Files.readString(logs.resolve("verification.txt")))
  }
}
