import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._
import metaconfig.{ConfDecoder, Configured, generic}

object RewriteVerification {
  case class Policy(target: String = "target/model-rewrite-fixture-v7", fixture: String = "tools/refactor/model-rewrite-fixture",
    report: String = "refactor-audit/models/pagination/rewrite-fixture.txt", mainClass: String = "migrationfixture.checkMigration", archiveLegacy: Boolean = true,
    rejectedConfigs: Map[String,String] = Map.empty)
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
}
class RewriteVerification(val policy: RewriteVerification.Policy) extends SyntacticRule("RewriteVerification") {
  def this() = this(RewriteVerification.Policy())
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("RewriteVerification")(RewriteVerification.Policy()).map(new RewriteVerification(_))
  override def afterComplete(): Unit = {
    val target = Paths.get(policy.target)
    require(!Files.exists(target),"Retain prior run; use a new output directory")
    Files.createDirectories(target)
    val fixture = Paths.get(policy.fixture)
    Files.copy(fixture.resolve("input/Models.scala"),target.resolve("Models.scala"))
    Files.copy(fixture.resolve("input/Types.scala"),target.resolve("Types.scala"))
    def run(name: String, args: List[String]): Unit = {
      println("Model rewrite verification: " + name)
      val code = new ProcessBuilder(args.asJava).redirectErrorStream(true).redirectOutput(target.resolve(name + ".log").toFile).start().waitFor()
      require(code == 0,"Failed " + name + "; inspect " + target.resolve(name + ".log"))
    }
    run("baseline",List("scala-cli","compile",target.toString,"--server=false","--semanticdb"))
    run("expected",List("scala-cli","compile",fixture.resolve("expected").toString,"--server=false"))
    val rejectArgs = List("scala-cli","run","--server=false","-q","-S","2.13.18","--dep","ch.epfl.scala:scalafix-cli_2.13.18:0.14.9","--main-class","scalafix.cli.Cli","--",
      "--config",fixture.resolve("reject-order.conf").toString,"--scala-version","3.10.0","--sourceroot",target.toString,"--auto-classpath","--auto-classpath-roots",target.toString,"--files",target.resolve("Models.scala").toString)
    val rejected = new ProcessBuilder(rejectArgs.asJava).redirectErrorStream(true).redirectOutput(target.resolve("reject-order.log").toFile).start().waitFor()
    require(rejected != 0 && Files.readString(target.resolve("reject-order.log")).contains("Reviewed constructor parameter order changed"),"Wrong-order configuration was not rejected for expected reason")
    require(Files.readString(target.resolve("Models.scala")) == Files.readString(fixture.resolve("input/Models.scala")),"Rejected rule modified fixture")
    policy.rejectedConfigs.toList.sortBy(_._1).zipWithIndex.foreach { case ((config,reason),index) =>
      require(reason.nonEmpty)
      val args = rejectArgs.updated(rejectArgs.indexOf("--config") + 1,config)
      val log = target.resolve("reject-extra-" + index + ".log")
      val code = new ProcessBuilder(args.asJava).redirectErrorStream(true).redirectOutput(log.toFile).start().waitFor()
      require(code != 0 && Files.readString(log).contains(reason), "Incorrect configured rejection: " + config)
      require(Files.readString(target.resolve("Models.scala")) == Files.readString(fixture.resolve("input/Models.scala")),"Rejected configuration modified fixture")
    }
    run("rewrite",List("scala-cli","run","--server=false","-q","-S","2.13.18","--dep","ch.epfl.scala:scalafix-cli_2.13.18:0.14.9","--main-class","scalafix.cli.Cli","--",
      "--config",fixture.resolve("rule.conf").toString,"--scala-version","3.10.0","--sourceroot",target.toString,"--auto-classpath","--auto-classpath-roots",target.toString,"--files",target.resolve("Models.scala").toString))
    require(Files.readString(target.resolve("Models.scala")) == Files.readString(fixture.resolve("expected/Models.scala")),"Output differs from frozen expected fixture")
    require(Files.readString(target.resolve("Types.scala")) == Files.readString(fixture.resolve("expected/Types.scala")))
    Files.copy(fixture.resolve("input/Checks.scala"),target.resolve("Checks.scala"))
    require(Files.readString(target.resolve("Checks.scala")) == Files.readString(fixture.resolve("expected/Checks.scala")))
    run("after",List("scala-cli","compile",target.toString,"--server=false"))
    run("behavior",List("scala-cli","run",target.toString,"--server=false","--main-class",policy.mainClass))
    val report = Paths.get(policy.report)
    Files.createDirectories(report.getParent)
    Files.writeString(report,"PASS: baseline, expected and rewritten definitions/callers compile; exact expected output matches.\n" +
      "The configured fixture covers defaults, positional/named construction, generated copy arguments, typed forwarding and unchanged unrelated records.\n" +
      "Wrong constructor order is rejected without modifying the source. Opaque types are defined in a separate file.\n" +
      "Configured runtime and compile-time assertions passed; inspect Checks.scala for the exact concepts, raw-value and substitution checks.\n" +
      "Development fixture only; coverage is exactly the configured input, expected output and behavioral checks. It does not establish whole-project caller closure or repair readiness.\n")
    println(Files.readString(report))
    val archive = Paths.get("refactor-audit/models/pagination/rewrite-runs")
    (if(policy.archiveLegacy) 1 to 7 else 1 to 0).foreach { version =>
      val source = Paths.get("target/model-rewrite-fixture-v" + version)
      val dest = archive.resolve("v" + version)
      Files.createDirectories(dest)
      val stream = Files.list(source)
      try stream.iterator.asScala.filter(p => Files.isRegularFile(p) && (p.toString.endsWith(".log") || p.toString.endsWith(".scala"))).foreach { p =>
        val output = dest.resolve(p.getFileName)
        if(Files.exists(output)) require(Files.readString(output) == Files.readString(p)) else Files.copy(p,output)
      } finally stream.close()
    }
  }
}
