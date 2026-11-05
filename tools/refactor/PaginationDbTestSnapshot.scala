import scalafix.v1._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class PaginationDbTestSnapshot extends SyntacticRule("PaginationDbTestSnapshot") {
  override def afterComplete(): Unit = {
    val out = Paths.get("refactor-audit/models/pagination/db-test-consumers/sources.tsv")
    require(!Files.exists(out))
    val before = Files.readAllLines(Paths.get("refactor-audit/models/pagination/build-v5/sources.tsv")).asScala.drop(1).map(_.split("\t")).map(a => a(0) -> a(1)).toMap
    val allowed = Set("modules/db/src/test/scala/com/histmersion/domain/catalogue/evidence/EvidenceFixtures.scala", "modules/db/src/test/scala/com/histmersion/db/InteractiveQueriesSuite.scala")
    val rows = before.toList.sortBy(_._1).map { case(p,h) =>
      val text = Files.readString(Paths.get(p))
      val next = java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
      require(next == h || allowed(p),"Unexpected change: " + p)
      if(allowed(p)) {
        val dest = out.getParent.resolve("sources").resolve(p)
        Files.createDirectories(dest.getParent); Files.writeString(dest,text)
      }
      p + "\t" + next
    }
    Files.createDirectories(out.getParent)
    Files.write(out,("path\tsha256" :: rows).asJava)
  }
}
