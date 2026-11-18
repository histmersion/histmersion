import scalafix.v1._
import java.nio.file.{Files,Paths}
import scala.jdk.CollectionConverters._

class TransactionPreviewVerification extends SyntacticRule("TransactionPreviewVerification") {
  override def afterComplete(): Unit = {
    val logs = Paths.get("target/transaction-preview-verification"); Files.createDirectories(logs)
    def run(label: String, args: List[String]): Unit = {
      val code = new ProcessBuilder(args.asJava).redirectErrorStream(true).redirectOutput(logs.resolve(label + ".log").toFile).start().waitFor()
      require(code == 0,s"$label failed ($code); see $logs/$label.log")
      println(label + ": PASS")
    }
    run("classpath",List("sbt","--server","-batch","set every scalafmtOnCompile := false","export server/Test/fullClasspath"))
    val exported = Files.readAllLines(logs.resolve("classpath.log")).asScala.filter(s => s.startsWith("List(") && s.endsWith(")") && s.contains("magnum_3")).toList
    require(exported.size == 1,"Expected one exported server test classpath")
    val entries = exported.head.stripPrefix("List(").stripSuffix(")").split(", ").toList.map(_.takeWhile(_ != '>')
      .replace("${OUT}",Paths.get("target/out").toAbsolutePath.toString)
      .replace("${CSR_CACHE}",Paths.get(System.getProperty("user.home"),"Library/Caches/Coursier/v1").toString))
    require(entries.forall(p => Files.isRegularFile(Paths.get(p))),"Unresolved evaluated classpath entry")
    run("preview",List("scala-cli","compile","target/transaction-repair-preview","--server=false","-S","3.10.1",
      "--scalac-option","-source:future","--classpath",entries.mkString(java.io.File.pathSeparator)))
    run("lifecycle",List("scala-cli","run","tools/refactor/transaction-repair/Transactor.scala","tools/refactor/transaction-repair/Lifecycle.scala",
      "--server=false","-S","3.10.1","--scalac-option","-source:future","--dep","com.augustnagro::magnum:1.3.1","--main-class","transactionfixture.Lifecycle"))
    val out = Paths.get("refactor-audit/transaction-repair/preview-verification.txt")
    Files.createDirectories(out.getParent)
    Files.write(out,List("PASS","All preview production and test sources compile against the evaluated server test classpath.",
      "Recording JDBC lifecycle fixtures pass; real database validation remains required after application.").asJava)
  }
}
