import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class OptionalRewriteVerification extends SyntacticRule("OptionalRewriteVerification") {
  override def afterComplete(): Unit = {
    val target = Paths.get("target/model-rewrite-optional-v1")
    require(!Files.exists(target),"Retain prior run")
    Files.createDirectories(target)
    val fixture = Paths.get("tools/refactor/model-rewrite-optional")
    val types = Paths.get("tools/refactor/model-rewrite-fixture/input/Types.scala")
    Files.copy(fixture.resolve("input/Models.scala"),target.resolve("Models.scala"))
    Files.copy(types,target.resolve("Types.scala"))
    def run(name: String, args: List[String]): Unit = {
      println("Optional rewrite verification: " + name)
      val code = new ProcessBuilder(args.asJava).redirectErrorStream(true).redirectOutput(target.resolve(name + ".log").toFile).start().waitFor()
      require(code == 0,"Failed " + name + "; inspect " + target.resolve(name + ".log"))
    }
    run("baseline",List("scala-cli","compile",target.toString,"--server=false","--semanticdb"))
    run("expected",List("scala-cli","compile",fixture.resolve("expected").toString,types.toString,"--server=false"))
    run("rewrite",List("scala-cli","run","--server=false","-q","-S","2.13.18","--dep","ch.epfl.scala:scalafix-cli_2.13.18:0.14.9","--main-class","scalafix.cli.Cli","--",
      "--config",fixture.resolve("rule.conf").toString,"--scala-version","3.10.0","--sourceroot",target.toString,"--auto-classpath","--auto-classpath-roots",target.toString,"--files",target.resolve("Models.scala").toString))
    require(Files.readString(target.resolve("Models.scala")) == Files.readString(fixture.resolve("expected/Models.scala")),"Output differs from frozen optional fixture")
    Files.copy(fixture.resolve("Checks.scala"),target.resolve("Checks.scala"))
    run("after",List("scala-cli","compile",target.toString,"--server=false"))
    run("behavior",List("scala-cli","run",target.toString,"--server=false","--main-class","migrationfixture.checkOptionalMigration"))
    val report = Paths.get("refactor-audit/models/pagination/optional-rewrite-fixture.txt")
    Files.writeString(report,"PASS: optional defaults, raw Option conversion, generated copy, method-result propagation and cross-record forwarding compile and preserve values. Exact expected output matches; raw Int constructor misuse is rejected.\nNo production fixes or complete transitive analysis claimed.\n")
    val archive = Paths.get("refactor-audit/models/pagination/optional-rewrite-runs/v1")
    Files.createDirectories(archive)
    val stream = Files.list(target)
    try stream.iterator.asScala.filter(p => Files.isRegularFile(p) && (p.toString.endsWith(".log") || p.toString.endsWith(".scala"))).foreach(p => Files.copy(p,archive.resolve(p.getFileName))) finally stream.close()
    println(Files.readString(report))
  }
}
