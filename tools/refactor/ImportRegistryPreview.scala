import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

class ImportRegistryPreview extends SyntacticRule("ImportRegistryPreview") {
  override def afterComplete(): Unit = {
    implicit val dialect: Dialect = dialects.Scala3
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    def rows(p: String) = Files.readAllLines(Paths.get(p)).asScala.drop(1).map(_.split("\t", -1).toList).toList
    val manifest = rows("refactor-audit/import-registry/api-codec-build-1/sources.tsv").map(r => r.head -> r(1)).toMap
    manifest.foreach { case(p,h) => require(hash(Files.readString(Paths.get(p))) == h, "Stale source: " + p) }
    val out = Paths.get("refactor-audit/import-registry/ownership-preview-1")
    require(!Files.exists(out))
    val sources = scala.collection.mutable.Map.empty[String, String]
    val edits = scala.collection.mutable.Map.empty[String, List[(Int, Int, String)]]
    val evidence = scala.collection.mutable.ListBuffer.empty[List[String]]
    val registry = "com.histmersion.domain.library.sources.referrers.SourceReferrers"
    val parameter = "(referrers: " + registry + ")"
    def db(p: String) = "modules/db/src/main/scala/com/histmersion/" + p + ".scala"
    def server(p: String) = "modules/server/src/main/scala/com/histmersion/" + p + ".scala"
    def tree(p: String): Source = {
      require(manifest.contains(p))
      sources.getOrElseUpdate(p, Files.readString(Paths.get(p))).parse[Source].get
    }
    def patch(p: String, start: Int, end: Int, value: String, ast: Tree, why: String): Unit = {
      edits.update(p, edits.getOrElse(p, Nil) :+ ((start, end, value)))
      evidence += List(p, (ast.pos.startLine + 1).toString, why, ast.structure, value)
    }
    def replace(p: String, before: String, after: String, count: Int = 1): Unit = {
      val pattern = before.parse[Stat].get.structure
      after.parse[Stat].get
      val found = tree(p).collect { case t if t.structure == pattern => t }
      require(found.size == count, "Unexpected AST count: " + p + " " + before + " = " + found.size)
      found.foreach(t => patch(p, t.pos.start, t.pos.end, after, t, "replace-exact-ast"))
    }
    def member(p: String, name: String): Defn.Val = {
      val found = tree(p).collect { case v: Defn.Val if v.pats.exists { case Pat.Var(n) => n.value == name; case _ => false } => v }
      require(found.size == 1); found.head
    }
    def factory(p: String, name: String): Unit = {
      val v = member(p, name)
      val keyword = v.tokens.find(_.is[Token.KwVal]).get
      val n = v.pats.head
      patch(p, keyword.pos.start, keyword.pos.end, "def", v, "factory-val-to-def")
      patch(p, n.pos.end, n.pos.end, parameter, v, "capture-registry")
    }
    def dropArgument(p: String, callee: String, expected: String, count: Int): Unit = {
      val shape = callee.parse[Term].get.structure
      val calls = tree(p).collect { case a: Term.Apply if a.fun.structure == shape => a }
      require(calls.size == count, "Call count: " + callee)
      calls.foreach { a =>
        val args = a.argClause.values
        require(args.size >= 2 && args.last.structure == expected.parse[Term].get.structure)
        patch(p, args(args.size - 2).pos.end, args.last.pos.end, "", a, "remove-transport-argument")
      }
    }
    def dropParameter(p: String, name: String): Unit = {
      val classes = tree(p).collect { case c: Defn.Class if c.name.value == name => c }
      require(classes.size == 1)
      val args = classes.head.ctor.paramClauses.flatMap(_.values)
      require(args.last.name.value == "sourceReferrers" && args.last.decltpe.get.syntax == "SourceReferrers")
      patch(p, args(args.size - 2).pos.end, args.last.pos.end, "", classes.head, "remove-transport-field")
      val imports = tree(p).collect { case i: Import if i.syntax == "import " + registry => i }
      require(imports.size == 1)
      imports.foreach(i => patch(p, i.pos.start, i.pos.end, "", i, "remove-library-import"))
      val comments = tree(p).tokens.collect { case c: Token.Comment if c.text.contains("`sourceReferrers`") => c }
      require(comments.size == 1)
      comments.foreach { c =>
        val start = sources(p).lastIndexOf('\n', c.pos.start - 1) + 1
        require(sources(p).substring(start,c.pos.start).forall(_ == ' '))
        patch(p, start, c.pos.end, "", classes.head, "remove-obsolete-transport-comment")
      }
    }

    val options = db("util/data/exchange/ImportOptions")
    dropParameter(options, "ImportOptions")
    val service = server("util/data/exchange/BundleService")
    dropParameter(service, "Wiring")
    dropArgument(service, "ImportOptions", "w.sourceReferrers", 1)
    dropArgument(server("server/ServerWiring"), "BundleService.Wiring", "Deps.sourceReferrers", 1)

    val library = server("domain/library/LibraryData")
    val owners = tree(library).collect { case o: Defn.Object if o.name.value == "LibraryData" => o }
    require(owners.size == 1)
    val owner = owners.head
    val keyword = owner.tokens.find(_.is[Token.KwObject]).get
    patch(library, keyword.pos.start, keyword.pos.end, "final class", owner, "contribution-receives-registry")
    patch(library, owner.name.pos.end, owner.name.pos.end, parameter, owner, "constructor-injection")
    replace(library, "DataOwner(\"library\")", "LibraryData.owner")
    replace(library, "Mirror.Records(\"sources\")", "LibraryData.SourceNotes")
    replace(library, "LibraryExchange.carried", "LibraryExchange.carried(referrers)")
    val companion = "\n\nobject LibraryData:\n  val owner: DataOwner = DataOwner(\"library\")\n  val SourceNotes: Mirror.Records = Mirror.Records(\"sources\")\n"
    companion.parse[Source].get
    patch(library, sources(library).length, sources(library).length, companion, owner, "retain-static-vocabulary")
    replace(server("server/Deps"), "com.histmersion.domain.library.LibraryData", "com.histmersion.domain.library.LibraryData(sourceReferrers)")

    val exchange = db("domain/library/LibraryExchange")
    factory(exchange, "carried")
    replace(exchange, "SourcesCarried.all", "SourcesCarried.all(referrers)")
    val carried = db("domain/library/sources/SourcesCarried")
    factory(carried, "sources")
    factory(carried, "all")
    replace(carried, "rows(10)(SourceBundleRepo.sourcesIn)", "rows(10)(w => SourceBundleRepo.sourcesIn(w, referrers))")
    replace(carried, "List(sources, findings, edges, unresolvedEdges, assessments, aliases)", "List(sources(referrers), findings, edges, unresolvedEdges, assessments, aliases)")
    val repo = db("domain/library/sources/SourceBundleRepo")
    val methods = tree(repo).collect { case d: Defn.Def if d.name.value == "sourcesIn" => d }
    require(methods.size == 1)
    val arg = methods.head.paramClauseGroups.flatMap(_.paramClauses).flatMap(_.values).last
    require(arg.name.value == "w")
    patch(repo, arg.pos.end, arg.pos.end, ", referrers: " + registry, methods.head, "importer-receives-registry")
    replace(repo, "w.opts.sourceReferrers", "referrers", 2)
    require(methods.head.body.collect { case g: Defn.GivenAlias if g.body.syntax == "w.transaction" => g }.size == 1)

    val testRoot = "modules/db/src/test/scala/com/histmersion/"
    replace(testRoot + "util/data/exchange/TestExchange.scala", "LibraryExchange.carried",
      "LibraryExchange.carried(com.histmersion.domain.library.sources.SourceFixtures.referrers)")
    List("BundleSuite" -> 1, "MergeSuite" -> 2, "MergeExtrasDbSuite" -> 2).foreach { case(name, count) =>
      dropArgument(testRoot + "db/" + name + ".scala", "BundleImport.Options", "SourceFixtures.referrers", count)
    }

    val changed = sources.toList.sortBy(_._1).map { case(p,before) =>
      val ordered = edits(p).sortBy(e => (e._1, e._2))
      require(ordered.sliding(2).forall { case List(a,b) => a._2 <= b._1; case _ => true }, "Overlapping patches")
      val next = ordered.reverse.foldLeft(before) { case(s,(a,b,v)) => s.patch(a,v,b-a) }
      next.parse[Source].get
      (p,before,next)
    }
    require(changed.size == 12)
    val prior = rows("refactor-audit/unread-projection/reconciliation-2/current-treatment.tsv")
    val selected = prior.filter(_(4) == "import-referrers")
    require(selected.size == 4 && selected.forall(r => r(1) == "production" && r(2) == "unresolved" && hash(sources(r(5))) == r(9)))
    Files.createDirectories(out)
    changed.foreach { case(p,b,n) =>
      List("before" -> b, "sources" -> n).foreach { case(dir,s) =>
        val dest = out.resolve(dir).resolve(p); Files.createDirectories(dest.getParent); Files.writeString(dest,s)
      }
    }
    def escape(s: String) = s.replace("\\","\\\\").replace("\t","\\t").replace("\n","\\n")
    Files.write(out.resolve("plan.tsv"), ("path\tbefore-sha256\tafter-sha256" :: changed.map { case(p,b,n) => List(p,hash(b),hash(n)).mkString("\t") }).asJava)
    Files.write(out.resolve("shapes.tsv"), ("path\tline\taction\tbefore-ast\treplacement" :: evidence.toList.map(_.map(escape).mkString("\t"))).asJava)
    Files.write(out.resolve("adjudication.tsv"), ("id\tbefore\tafter\ttreatment\tevidence" :: selected.map(r =>
      List(r.head,"unresolved","confirmed","manual/design", "Only library sourcesIn consumes the registry; inject through the library carried-kind factory, retaining both rename paths and the writer transaction.").mkString("\t"))).asJava)
    val treatment = prior.map(r => if(selected.exists(_.head == r.head)) r.updated(2,"confirmed").updated(3,"manual/design").updated(10,"Confirmed library-owned dependency transported through utility options; ownership injection preview records resolution of the earlier uncertainty.") else r)
    val header = Files.readAllLines(Paths.get("refactor-audit/unread-projection/reconciliation-2/current-treatment.tsv")).get(0)
    Files.write(out.resolve("prior-treatment.tsv"), (header :: treatment.map(_.mkString("\t"))).asJava)
    Files.writeString(out.resolve("status.txt"), "PREVIEW_ONLY\n12 files; four original findings adjudicated, not repaired. Exact frozen design batch, no generalized bulk-automation claim.\n")
    println("12 files, " + evidence.size + " AST/token patches; four original identities confirmed")
  }
}
