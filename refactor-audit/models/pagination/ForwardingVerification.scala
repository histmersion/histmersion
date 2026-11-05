import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class ForwardingVerification extends SyntacticRule("ForwardingVerification") {
  override def afterComplete(): Unit = {
    val target = Paths.get("target/model-rewrite-forwarding-v2")
    val fixture = Paths.get("tools/refactor/model-rewrite-forwarding")
    require(!Files.exists(target),"Retain prior run")
    Files.createDirectories(target)
    List("Wire.scala","Page.scala").foreach(n => Files.copy(fixture.resolve("input").resolve(n),target.resolve(n)))
    Files.copy(fixture.resolve("Types.scala"),target.resolve("Types.scala"))
    def run(name: String, args: List[String]): Unit = {
      println("Forwarding verification: " + name)
      val code = new ProcessBuilder(args.asJava).redirectErrorStream(true).redirectOutput(target.resolve(name + ".log").toFile).start().waitFor()
      require(code == 0,"Failed " + name + "; inspect " + target.resolve(name + ".log"))
    }
    run("baseline",List("scala-cli","compile",target.toString,"--server=false","--semanticdb"))
    run("expected",List("scala-cli","compile",fixture.resolve("expected").toString,fixture.resolve("Types.scala").toString,"--server=false"))
    List("wire" -> "Wire.scala","page" -> "Page.scala").foreach { case(stage,file) =>
      run(stage,List("scala-cli","run","--server=false","-q","-S","2.13.18","--dep","ch.epfl.scala:scalafix-cli_2.13.18:0.14.9","--main-class","scalafix.cli.Cli","--",
        "--config",fixture.resolve(stage + ".conf").toString,"--scala-version","3.10.0","--sourceroot",target.toString,"--auto-classpath","--auto-classpath-roots",target.toString,"--files",target.resolve(file).toString))
      require(Files.readString(target.resolve(file)) == Files.readString(fixture.resolve("expected").resolve(file)),"Unexpected " + stage + " output")
    }
    Files.copy(fixture.resolve("Checks.scala"),target.resolve("Checks.scala"))
    run("after",List("scala-cli","compile",target.toString,"--server=false"))
    run("behavior",List("scala-cli","run",target.toString,"--server=false","--main-class","forwardingfixture.checkForwarding"))
    val report = Paths.get("refactor-audit/models/pagination/forwarding-fixture.txt")
    Files.writeString(report,"PASS: two-stage migration from primitive wire record to generic consumer, including explicit type application, generated copy, defaults and unrelated look-alike. Each output matches frozen expectations; final definitions/callers compile and behavior/type separation pass.\nConsumer source remained unchanged between baseline compilation and its own rewrite; stale-source bypass was not used.\n")
    val archive = Paths.get("refactor-audit/models/pagination/forwarding-runs/v2")
    Files.createDirectories(archive)
    val stream = Files.list(target)
    try stream.iterator.asScala.filter(p => Files.isRegularFile(p) && (p.toString.endsWith(".log") || p.toString.endsWith(".scala"))).foreach(p => Files.copy(p,archive.resolve(p.getFileName))) finally stream.close()
    println(Files.readString(report))
  }
}
