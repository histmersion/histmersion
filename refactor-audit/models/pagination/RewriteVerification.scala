import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class RewriteVerification extends SyntacticRule("RewriteVerification") {
  override def afterComplete(): Unit = {
    val target = Paths.get("target/model-rewrite-fixture-v7")
    require(!Files.exists(target),"Retain prior run; use a new output directory")
    Files.createDirectories(target)
    val fixture = Paths.get("tools/refactor/model-rewrite-fixture")
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
    run("rewrite",List("scala-cli","run","--server=false","-q","-S","2.13.18","--dep","ch.epfl.scala:scalafix-cli_2.13.18:0.14.9","--main-class","scalafix.cli.Cli","--",
      "--config",fixture.resolve("rule.conf").toString,"--scala-version","3.10.0","--sourceroot",target.toString,"--auto-classpath","--auto-classpath-roots",target.toString,"--files",target.resolve("Models.scala").toString))
    require(Files.readString(target.resolve("Models.scala")) == Files.readString(fixture.resolve("expected/Models.scala")),"Output differs from frozen expected fixture")
    require(Files.readString(target.resolve("Types.scala")) == Files.readString(fixture.resolve("expected/Types.scala")))
    Files.copy(fixture.resolve("input/Checks.scala"),target.resolve("Checks.scala"))
    require(Files.readString(target.resolve("Checks.scala")) == Files.readString(fixture.resolve("expected/Checks.scala")))
    run("after",List("scala-cli","compile",target.toString,"--server=false"))
    run("behavior",List("scala-cli","run",target.toString,"--server=false","--main-class","migrationfixture.checkMigration"))
    val report = Paths.get("refactor-audit/models/pagination/rewrite-fixture.txt")
    Files.writeString(report,"PASS: baseline, expected and rewritten definitions/callers compile; exact expected output matches.\n" +
      "Covers defaults, positional/named construction, generated copy arguments, forwarding typed fields and an unrelated text-offset look-alike.\n" +
      "Wrong constructor order is rejected without modifying the source. Opaque types are defined in a separate file.\n" +
      "Runtime assertions preserve values; compile-time checks reject raw Int and RowCount as Offset and raw Int at the migrated constructor.\n" +
      "Development fixture only. Option leaves, arithmetic, method arguments, inferred results and cross-file transitive closure remain unsupported; no production application authorized by this check alone.\n")
    println(Files.readString(report))
    val archive = Paths.get("refactor-audit/models/pagination/rewrite-runs")
    (1 to 7).foreach { version =>
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
