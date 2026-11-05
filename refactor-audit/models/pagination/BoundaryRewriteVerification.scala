import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class BoundaryRewriteVerification extends SyntacticRule("BoundaryRewriteVerification") {
  override def afterComplete(): Unit = {
    val target = Paths.get("target/model-rewrite-boundaries-v1")
    val fixture = Paths.get("tools/refactor/model-rewrite-boundaries")
    require(!Files.exists(target),"Retain prior run")
    Files.createDirectories(target)
    Files.copy(fixture.resolve("input/Models.scala"),target.resolve("Models.scala"))
    def run(name: String, args: List[String]): Unit = {
      println("Boundary rewrite verification: " + name)
      val code = new ProcessBuilder(args.asJava).redirectErrorStream(true).redirectOutput(target.resolve(name + ".log").toFile).start().waitFor()
      require(code == 0,"Failed " + name + "; inspect " + target.resolve(name + ".log"))
    }
    run("baseline",List("scala-cli","compile",target.toString,"--server=false","--semanticdb"))
    run("expected",List("scala-cli","compile",fixture.resolve("expected").toString,"--server=false"))
    run("rewrite",List("scala-cli","run","--server=false","-q","-S","2.13.18","--dep","ch.epfl.scala:scalafix-cli_2.13.18:0.14.9","--main-class","scalafix.cli.Cli","--",
      "--config",fixture.resolve("rule.conf").toString,"--scala-version","3.10.0","--sourceroot",target.toString,"--auto-classpath","--auto-classpath-roots",target.toString,"--files",target.resolve("Models.scala").toString))
    require(Files.readString(target.resolve("Models.scala")) == Files.readString(fixture.resolve("expected/Models.scala")),"Boundary output differs from frozen fixture")
    run("after",List("scala-cli","compile",target.toString,"--server=false"))
    run("behavior",List("scala-cli","run",target.toString,"--server=false","--main-class","boundaryfixture.checkBoundaries"))
    val report = Paths.get("refactor-audit/models/pagination/boundary-rewrite-fixture.txt")
    Files.writeString(report,"PASS: real Iron Pure newtypes; arithmetic, zero pattern matching and configured rendering argument unwrap only at selected boundaries. Typed forwarding and unrelated text offsets remain unchanged. Baseline/expected/after compile; exact output and behavior pass.\nDevelopment fixture, not independent quality evidence.\n")
    val archive = Paths.get("refactor-audit/models/pagination/boundary-rewrite-runs/v1")
    Files.createDirectories(archive)
    val stream = Files.list(target)
    try stream.iterator.asScala.filter(p => Files.isRegularFile(p) && (p.toString.endsWith(".log") || p.toString.endsWith(".scala"))).foreach(p => Files.copy(p,archive.resolve(p.getFileName))) finally stream.close()
    println(Files.readString(report))
  }
}
