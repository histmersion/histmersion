import scalafix.v1._
import scala.meta._
import java.nio.file.{Files,Paths}
import scala.jdk.CollectionConverters._

class TransactionAppliedVerification extends SyntacticRule("TransactionAppliedVerification") {
  override def afterComplete(): Unit = {
    implicit val dialect: Dialect = dialects.Scala3
    def hash(s: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val rows = Files.readAllLines(Paths.get("refactor-audit/transaction-repair/plan.tsv")).asScala.drop(1).map(_.split("\t",-1)).toList
    rows.foreach { r =>
      val actual = Files.readString(Paths.get(r(1)))
      val trivia = Paths.get("refactor-audit/transaction-repair/trivia", Paths.get(r(1)).getFileName.toString.stripSuffix(".scala") + ".txt")
      if(Files.exists(trivia)) {
        val evidence = Files.readAllLines(trivia).asScala.toList
        require(evidence == List("AST_UNCHANGED", "before=" + r(3), "after=" + hash(actual)), "Unrecorded trivia correction: " + r(1))
      } else require(hash(actual) == r(3),"Applied source differs from preview: " + r(1))
      require(actual.parse[Source].get.structure == Files.readString(Paths.get("target/transaction-repair-preview",r(1))).parse[Source].get.structure)
      if(r(0) != r(1)) require(!Files.exists(Paths.get(r(0))),"Original moved test still exists")
    }
    Files.write(Paths.get("refactor-audit/transaction-repair/final-applied-verification.txt"),List("PASS",s"${rows.size} files match the compiled AST preview with recorded trivia-only corrections; old test paths removed.").asJava)
    println("PASS: applied sources match the compiled preview")
  }
}
