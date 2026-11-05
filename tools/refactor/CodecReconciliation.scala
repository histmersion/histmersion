import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class CodecReconciliation extends SyntacticRule("CodecReconciliation") {
  override def afterComplete(): Unit = {
    val base = "refactor-audit/codec-ownership/"
    def lines(p: String) = Files.readAllLines(Paths.get(p)).asScala.toList
    def rows(p: String) = lines(p).drop(1).map(_.split("\t", -1).toList)
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    def read(p: String) = Files.readString(Paths.get(p))
    def tree(p: String) = dialects.Scala3(read(p)).parse[Source].get
    val root = Paths.get("").toAbsolutePath.normalize
    def relative(p: String) = root.relativize(Paths.get(p).toAbsolutePath.normalize).toString
    val out = Paths.get(base + "reconciliation-1")
    require(!Files.exists(out))
    require(lines(base + "build-1/status.txt").head == "PASS")
    val built = rows(base + "build-1/sources.tsv").map(r => r.head -> r(1)).toMap
    built.foreach { case (p,h) => require(hash(read(p)) == h, "Stale build: " + p) }
    List("architecture", "models").foreach { family =>
      require(!lines(s"refactor-audit/$family/status.txt").contains("RUNNING"))
      require(rows(s"refactor-audit/$family/sources.tsv").map(r => relative(r.head) -> r(1)).toMap == built)
    }
    val dirs = Files.list(Paths.get("refactor-audit/isolation-history/observations"))
    val latest = try dirs.iterator.asScala.filter(Files.isDirectory(_)).toList.sortBy(_.toString).last finally dirs.close()
    require(lines(latest.resolve("status.txt").toString).head == "PASS")
    val inputs = lines(latest.resolve("inputs.txt").toString)
    require(inputs.contains("sources=" + hash(lines("refactor-audit/architecture/sources.tsv").mkString("\n"))))
    require(inputs.contains("crossings=" + hash(read("refactor-audit/architecture/dependency-crossings.tsv"))))
    val before = rows("refactor-audit/isolation-triage/review-2/observation.tsv").map(r => r.head -> r).toMap
    val decisions = rows("refactor-audit/isolation-triage/review-2/treatment/findings.tsv")
    val selected = decisions.filter(r => Set("feature-codecs", "unused-domain-imports")(r(4)))
    require(selected.size == 29 && selected.forall(r => r(1) == "production" && r(2) == "confirmed"))
    val ids = selected.map(_.head).toSet
    val after = rows(latest.resolve("findings.tsv").toString).map(r => r.head -> r).toMap
    require(before.keySet -- after.keySet == ids, "Unexpected removals")
    require((after.keySet -- before.keySet).isEmpty, "New identities")
    require(after.forall { case (id,r) => r.take(9) == before(id).take(9) }, "Residual evidence changed")
    rows(base + "preview-2/plan.tsv").foreach(r => require(built(r.head) == r(2), "Patch differs: " + r.head))
    val codecPath = "modules/db/src/main/scala/com/histmersion/util/database/Codecs.scala"
    def givens(p: String) = tree(p).collect { case g: Defn.GivenAlias => g.decltpe.syntax -> g.structure }.toMap
    val original = givens(base + "preview-2/before/" + codecPath)
    rows(base + "preview-2/extractions.tsv").filter(_(2) == "extract").foreach { r =>
      require(givens(r(3))(r(1)) == original(r(1)), "Codec AST changed: " + r(1))
    }
    require(givens(codecPath).forall { case (t,ast) => original(t) == ast })
    val suites = List("SourceColumnsSuite", "FindingColumnsSuite", "PlaceColumnsSuite")
    suites.foreach { suite =>
      val p = built.keys.find(_.endsWith("/" + suite + ".scala")).get
      def classes(path: String) = tree(path).collect { case c: Defn.Class => c.structure }
      require(classes(p) == classes(base + "preview-2/before/" + p), "Test body changed")
      List("tests-before", "build-1").foreach { stage =>
        require(lines(base + stage + "/build.log").exists(_.contains(suite + " finished: 0 failed, 0 ignored, 3 total")))
      }
    }
    val oldRefs = rows(base + "compiled-1/references.tsv")
    val newRefs = rows(base + "compiled-2/references.tsv")
    val oldOwner = "com/histmersion/util/database/Codecs$package$"
    val providers = Map("given_DbCodec_SourceStatus" -> "com/histmersion/domain/library/sources/SourceColumns$",
      "given_DbCodec_Option" -> "com/histmersion/domain/library/findings/FindingColumns$",
      "given_DbCodec_PlacePrecision" -> "com/histmersion/domain/catalogue/places/PlaceColumns$")
    val consumers = oldRefs.filter(r => r(2) == "external-consumer" && providers.contains(r(4)))
    require(consumers.size == 5)
    consumers.foreach { r =>
      require(newRefs.exists(n => n(1) == r(1) && n(3) == providers(r(4)) && n(4) == r(4) && n(5) == r(5)), "Lost generated consumer: " + r(1))
    }
    require(!newRefs.exists(r => r(2) == "external-consumer" && r(3) == oldOwner && providers.contains(r(4))))
    def generic(rs: List[List[String]]) = rs.filter(r => r(2) == "external-consumer" && r(3) == oldOwner && r(4) == "given_DbCodec_List").map(r => List(r(1),r(3),r(4),r(5))).toSet
    require(generic(oldRefs).size == 89 && generic(oldRefs) == generic(newRefs), "Generic consumers changed")
    require(!oldRefs.exists(r => r(2) == "external-consumer" && r(4) == "given_DbCodec_EdgeKind"))
    require(!newRefs.exists(r => r(2) == "external-consumer" && r(4) == "given_DbCodec_EdgeKind"))
    Files.createDirectories(out)
    def write(name: String, data: List[String]) = Files.write(out.resolve(name), data.asJava)
    write("events.tsv", "id\tevent\tevidence" :: selected.map(r => r.head + "\tverified-fixed\t" + latest))
    write("current-treatment.tsv", lines("refactor-audit/isolation-triage/review-2/treatment/findings.tsv").head :: decisions.filterNot(r => ids(r.head)).map(_.mkString("\t")))
    write("compiled-consumers.tsv", "class\told-owner\tnew-owner\tmember\tdescriptor" :: consumers.map(r => List(r(1),r(3),providers(r(4)),r(4),r(5)).mkString("\t")))
    write("status.txt", List("PASS", "verified-production-relationships=29", "remaining-production=126", "remaining-test=191", "new=0", "generated-consumers-preserved=5", "generic-consumers-preserved=89", "codec-tests-before-and-after=9", "source-files=" + built.size, "isolation-observation=" + latest,
      "JVM compiled references are bounded evidence; reflection, external consumers and native-image behavior remain unestablished. No family precision/recall claim or aggregate automation percentage follows from this batch."))
    println(lines(out.resolve("status.txt").toString).mkString("\n"))
  }
}
