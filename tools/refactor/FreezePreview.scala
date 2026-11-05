import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

object FreezePreview {
  case class Policy(preview: String = "", report: String = "", sourceHashes: String = "", expectedFiles: Int = 0)
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
}
class FreezePreview(val policy: FreezePreview.Policy) extends SyntacticRule("FreezePreview") {
  def this() = this(FreezePreview.Policy())
  private implicit val dialect: Dialect = dialects.Scala3
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("FreezePreview")(FreezePreview.Policy()).map(new FreezePreview(_))
  private def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
  override def afterComplete(): Unit = {
    val root = Paths.get(policy.preview); val out = Paths.get(policy.report)
    require(policy.expectedFiles > 0 && !Files.exists(out))
    val frozen = Files.readAllLines(Paths.get(policy.sourceHashes)).asScala.drop(1).map(_.split("\t")).map(a => a(0) -> a(1)).toMap
    val stream = Files.walk(root)
    val paths = try stream.iterator.asScala.filter(Files.isRegularFile(_)).toList.sortBy(_.toString) finally stream.close()
    require(paths.size == policy.expectedFiles)
    val rows = paths.map { p =>
      val relative = root.relativize(p).toString
      require(relative.startsWith("modules/") && relative.endsWith(".scala"))
      val before = Files.readString(Paths.get(relative)); require(hash(before) == frozen(relative))
      val next = Files.readString(p); next.parse[Source].get
      require(before != next,"Preview contains unchanged file")
      (relative,before,next)
    }
    rows.foreach { case(p,b,n) =>
      List("before" -> b,"sources" -> n).foreach { case(dir,s) => val dest = out.resolve(dir).resolve(p); Files.createDirectories(dest.getParent); Files.writeString(dest,s) }
    }
    Files.write(out.resolve("plan.tsv"),("path\tbefore-sha256\tafter-sha256" :: rows.map { case(p,b,n) => s"$p\t${hash(b)}\t${hash(n)}" }).asJava)
    println(s"Frozen ${rows.size} changed sources; application unchanged")
  }
}
