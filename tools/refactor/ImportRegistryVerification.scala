import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class ImportRegistryVerification extends SyntacticRule("ImportRegistryVerification") {
  override def afterComplete(): Unit = {
    implicit val dialect: Dialect = dialects.Scala3
    val root = Paths.get("refactor-audit/import-registry")
    val preview = root.resolve("ownership-preview-1")
    def rows(p: java.nio.file.Path) = Files.readAllLines(p).asScala.drop(1).map(_.split("\t", -1).toList).toList
    def read(p: java.nio.file.Path) = Files.readString(p)
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    def manifest(p: java.nio.file.Path) = rows(p).map(r => Paths.get(r.head).toAbsolutePath.normalize.toString -> r(1)).toMap
    val plan = rows(preview.resolve("plan.tsv"))
    require(plan.size == 12)
    val before = manifest(root.resolve("api-codec-build-1/sources.tsv"))
    val built = manifest(root.resolve("ownership-build-1/sources.tsv"))
    val changes = plan.map(r => Paths.get(r.head).toAbsolutePath.normalize.toString -> r(2)).toMap
    require(read(root.resolve("ownership-build-1/status.txt")).startsWith("PASS\n"))
    require(built == before ++ changes && built.size == before.size, "Source universe changed outside the patch")
    require(manifest(Paths.get("refactor-audit/architecture/sources.tsv")) == built)
    require(manifest(Paths.get("refactor-audit/models/sources.tsv")) == built)
    built.foreach { case(p,h) => require(hash(read(Paths.get(p))) == h, "Stale compilation") }
    plan.foreach { r => require(hash(read(preview.resolve("before").resolve(r.head))) == r(1)) }
    def source(p: String, old: Boolean): Source = read(if(old) preview.resolve("before").resolve(p) else Paths.get(p)).parse[Source].get
    val repo = "modules/db/src/main/scala/com/histmersion/domain/library/sources/SourceBundleRepo.scala"
    def importer(old: Boolean) = source(repo,old).collect { case d: Defn.Def if d.name.value == "sourcesIn" => d }.head
    val registryRead = "w.opts.sourceReferrers".parse[Term].get.structure
    val expectedBody = importer(true).body.transform { case t: Term if t.structure == registryRead => Term.Name("referrers") }
    require(expectedBody.structure == importer(false).body.structure, "Rename behavior or transaction body changed")
    val deps = "modules/server/src/main/scala/com/histmersion/server/Deps.scala"
    def registry(old: Boolean) = source(deps,old).collect {
      case v: Defn.Val if v.pats.exists { case Pat.Var(n) => n.value == "sourceReferrers"; case _ => false } => v
    }.head
    require(registry(true).structure == registry(false).structure, "Referrer registry or order changed")
    val testsBefore = rows(root.resolve("api-codec-tests/baseline.tsv"))
    val testsAfter = rows(root.resolve("ownership-tests/baseline.tsv"))
    require(testsBefore.size == 39 && testsBefore.forall(_(2) == "passed"))
    require(testsAfter.map(_.take(2)).toSet == testsBefore.map(_.take(2)).toSet && testsAfter.size == 39)
    require(testsAfter.forall(_(2) == "passed"))
    val serverTests = rows(root.resolve("ownership-server-tests/baseline.tsv"))
    require(serverTests.nonEmpty && serverTests.forall(_(2) == "passed"))
    val transport = "BundleImport.Options".parse[Term].get.structure
    plan.filter(_.head.contains("/src/test/")).foreach { r =>
      val old = source(r.head,true)
      def normalize(t: Tree): Tree = t.transform {
        case a: Term.Apply if a.fun.structure == transport && a.argClause.values.size == 4 =>
          require(a.argClause.values.last.syntax == "SourceFixtures.referrers")
          a.copy(fun = a.fun, argClause = a.argClause.copy(values = a.argClause.values.dropRight(1)))
        case a: Term.Apply if a.fun.structure == "LibraryExchange.carried".parse[Term].get.structure =>
          require(a.argClause.values.map(_.syntax) == List("com.histmersion.domain.library.sources.SourceFixtures.referrers"))
          a.fun
      }
      require(normalize(old).structure == normalize(source(r.head,false)).structure, "Test body changed outside registry injection: " + r.head)
    }
    val prior = rows(preview.resolve("prior-treatment.tsv"))
    val selected = prior.filter(_(4) == "import-referrers").map(_.head).toSet
    require(selected.size == 4 && prior.filter(r => selected(r.head)).forall(r => r(2) == "confirmed" && r(3) == "manual/design"))
    val dirs = Files.list(Paths.get("refactor-audit/isolation-history/observations"))
    val observation = try dirs.iterator.asScala.filter(Files.isDirectory(_)).toList.sortBy(_.getFileName.toString).last finally dirs.close()
    val status = Files.readAllLines(observation.resolve("status.txt")).asScala
    require(status.head == "PASS" && status.contains("new=0") && status.contains("reopened=0"))
    val inputs = Files.readAllLines(observation.resolve("inputs.txt")).asScala
    val sourceManifestText = Files.readAllLines(Paths.get("refactor-audit/architecture/sources.tsv")).asScala.mkString("\n")
    require(inputs.contains("sources=" + hash(sourceManifestText)), "Identity observation has a different source manifest")
    require(inputs.contains("crossings=" + hash(read(Paths.get("refactor-audit/architecture/dependency-crossings.tsv")))))
    val current = rows(observation.resolve("findings.tsv")).map(r => r.head -> r).toMap
    val retired = "ISO-c7871b2512cb3d4fb9b421a458fe9d24d9b993419792a42f0941f2399abb081a"
    val replacement = "ISO-a24935b1dfded4abea60f7f43a9c7dcbe4add42ac65095c71958fb0875fbc1b7"
    val oldFactory = prior.find(_.head == retired).get
    val newFactory = current(replacement)
    require(oldFactory(1) == "test" && oldFactory(5) == newFactory(3) && oldFactory(6) == newFactory(4))
    require(oldFactory(7) == "com/histmersion/domain/library/LibraryExchange.carried.")
    require(newFactory(5) == "com/histmersion/domain/library/LibraryExchange.carried().")
    require(prior.map(_.head).toSet -- current.keySet == selected + retired, "Removed unrelated finding identities")
    val added = current.keySet -- prior.map(_.head).toSet
    val review = root.resolve("ownership-identity-review/tests/decisions.tsv")
    val reviewed = if(Files.exists(review)) rows(review) else Nil
    require(added == reviewed.map(_.head).toSet, "Unreviewed new identities")
    val updated = prior.filterNot(r => selected(r.head) || r.head == retired).map { r =>
      val c = current(r.head)
      require(c(1) == r(1) && c(3) == r(5) && c(4) == r(6) && c(5) == r(7) && c(6) == r(8))
      r.updated(9,hash(read(Paths.get(r(5)))))
    } ++ reviewed.map { r =>
      val c = current(r.head)
      require(c(1) == "test" && r(3) == "non-violation" && hash(read(Paths.get(r(1)))) == r(2))
      List(r.head,"test","non-violation","no-rewrite","registry-test-composition",r(1),c(4),c(5),c(6),r(2),r(4))
    }
    val out = root.resolve("ownership-verification")
    require(!Files.exists(out)); Files.createDirectories(out)
    val header = Files.readAllLines(preview.resolve("prior-treatment.tsv")).get(0)
    Files.write(out.resolve("current-treatment.tsv"), (header :: updated.sortBy(_.head).map(_.mkString("\t"))).asJava)
    Files.write(out.resolve("events.tsv"), ("id\tevent\ttreatment" :: (selected.toList.sorted.map(_ + "\tverified-fixed\tmanual/design") :+
      (retired + "\tidentity-replaced-by:" + replacement + "\tno-repair-credit"))).asJava)
    Files.write(out.resolve("status.txt"), List("PASS", "Four original confirmed production relationships repaired; no bulk-automation credit.",
      "39 database tests pass before/after with identical test ASTs except registry transport.",
      "Supplementary server tests passed=" + serverTests.size,
      "Importer body, writer transaction and referrer order unchanged. Entire compiled source universe reconciled.",
      "observation=" + observation).asJava)
    println("PASS: four original isolation relationships fixed; all tests and source-universe checks pass")
  }
}
