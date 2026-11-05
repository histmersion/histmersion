import scalafix.v1._
import scala.meta._
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._
import metaconfig.{ConfDecoder, Configured, generic}

object Preview {
  case class Method(owner: String = "", parameters: List[String] = Nil, fields: Map[String,String] = Map.empty, setFields: List[String] = Nil)
  object Method {
    implicit val surface: generic.Surface[Method] = generic.deriveSurface[Method]
    implicit val decoder: ConfDecoder[Method] = generic.deriveDecoder(Method()).noTypos
  }
  case class Policy(review: String = "", target: String = "target/pagination-preview-v3", report: String = "refactor-audit/models/pagination/preview-v3",
    scalarBoundaryOwners: List[String] = Nil, scalarBoundaryCalls: Map[String,List[Int]] = Map.empty,
    containerBoundaryOwners: List[String] = Nil, containerBoundaryCalls: Map[String,List[Int]] = Map.empty,
    widenNumericBoundaries: Boolean = false, methods: List[Method] = Nil, widenStringTypes: List[String] = Nil,
    results: Map[String,String] = Map.empty)
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
}
class Preview(val policy: Preview.Policy) extends SyntacticRule("Preview") {
  def this() = this(Preview.Policy())
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("Preview")(Preview.Policy()).map(new Preview(_))
  private implicit val dialect: Dialect = dialects.Scala3
  private def reviewedPreview(): Unit = {
    val root = Paths.get("").toAbsolutePath.normalize
    val target = root.resolve(policy.target); val report = root.resolve(policy.report)
    require(!Files.exists(target) && !Files.exists(report), "Preserve existing preview")
    def rows(path: String) = Files.readAllLines(Paths.get(path)).asScala.tail.map(_.split("\t",-1).toList).toList
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    def quote(s: String) = Lit.String(s).syntax
    val sources = rows("refactor-audit/models/sources.tsv")
    def unchanged(): Unit = sources.foreach(r => require(hash(Files.readString(Paths.get(r.head))) == r(1), "Stale source: " + r.head))
    unchanged()
    val review = rows(policy.review)
    require(review.nonEmpty && review.map(_.head).distinct.size == review.size && review.forall(r => r(4) == "confirmed" && r(8) == "api" && r(9) == "Pure" && r(10) == "bulk-rewrite"))
    review.foreach(r => require(hash(Files.readString(Paths.get(r(12)))) == r(13), "Stale purpose review"))
    val positions = rows("refactor-audit/models/positions.tsv").map(r => r.head -> r).toMap
    val leaves = rows("refactor-audit/models/leaves.tsv")
    val catalogue = rows("refactor-audit/models/types.tsv")
    val cache = scala.collection.mutable.Map.empty[String,Source]
    def source(path: String): Source = cache.getOrElseUpdate(path,Files.readString(Paths.get(path)).parse[Source].get)
    val bases = review.groupBy(_(6)).toList.sortBy(_._1).map { case (name,rs) =>
      val found = rs.map { r =>
        val matches = leaves.filter(l => l.head == r(1) && l(1) == r(2) && l(2) == "primitive")
        require(matches.size == 1); matches.head(3)
      }.distinct
      require(found.size == 1, "Conflicting bases for proposed concept: " + name)
      name -> found.head
    }.toMap
    val configs = review.groupBy(r => positions(r(1))(6)).toList.sortBy(_._1).map { case(owner,rs) =>
      val entries = catalogue.filter(_.head == owner); require(entries.size == 1 && entries.head(3) == "case-class")
      val entry = entries.head
      val definitions = source(entry(1)).collect { case c: Defn.Class if c.pos.startLine + 1 == entry(2).toInt => c }
      require(definitions.size == 1 && definitions.head.ctor.paramClauses.size == 1)
      val params = definitions.head.ctor.paramClauses.head.values
      val fields = rs.map(r => positions(r(1))(8) -> r(6)).toMap
      require(fields.size == rs.size && fields.keySet.subsetOf(params.map(_.name.value).toSet))
      def shape(p: Term.Param): String = p.decltpe.get match {
        case a: Type.Apply if a.tpe.syntax == "Option" && a.argClause.values.size == 1 => "option"
        case a: Type.Apply if a.tpe.syntax == "List" && a.argClause.values.size == 1 => "list"
        case _: Type.Name => "scalar"
        case t => sys.error("Unsupported reviewed field shape: " + t.structure)
      }
      val selected = params.filter(p => fields.contains(p.name.value))
      "{owner=" + quote(owner) + ",parameters=[" + params.map(p => quote(p.name.value)).mkString(",") +
        "],fields={" + fields.toList.sorted.map { case(n,t) => quote(n) + "=" + quote(t) }.mkString(",") +
        "},optionalFields=[" + selected.filter(shape(_) == "option").map(p => quote(p.name.value)).mkString(",") +
        "],listFields=[" + selected.filter(shape(_) == "list").map(p => quote(p.name.value)).mkString(",") + "]}"
    }
    Files.createDirectories(target); Files.createDirectories(report)
    def owners(name: String, values: List[String]) = name + "=[" + values.map(quote).mkString(",") + "]\n"
    def calls(name: String, values: Map[String,List[Int]]) = name + "={" + values.toList.sortBy(_._1).map { case(s,is) => quote(s) + "=[" + is.mkString(",") + "]" }.mkString(",") + "}\n"
    val methods = policy.methods.map { m =>
      require(m.fields.values.forall(bases.contains),"Method target must belong to reviewed concepts")
      "{owner=" + quote(m.owner) + ",parameters=[" + m.parameters.map(quote).mkString(",") + "],fields={" +
        m.fields.toList.sortBy(_._1).map { case(n,t) => quote(n) + "=" + quote(t) }.mkString(",") +
        "},setFields=[" + m.setFields.map(quote).mkString(",") + "]}"
    }.mkString("methods=[",",","]\n")
    require(policy.results.values.forall(bases.contains),"Result target must belong to reviewed concepts")
    val results = policy.results.toList.sortBy(_._1).map { case(s,t) => quote(s) + "=" + quote(t) }.mkString("results={",",","}\n")
    val config = "rules=[\"file:tools/refactor/ModelTypeRewrite.scala\"]\nModelTypeRewrite {\nrecords=[\n" + configs.mkString(",\n") +
      "\n]\nprimitiveTypes={" + bases.toList.sorted.map { case(n,b) => quote(n) + "=" + quote(b) }.mkString(",") +
      "}\n" + owners("scalarBoundaryOwners",policy.scalarBoundaryOwners) + calls("scalarBoundaryCalls",policy.scalarBoundaryCalls) +
      owners("containerBoundaryOwners",policy.containerBoundaryOwners) + calls("containerBoundaryCalls",policy.containerBoundaryCalls) +
      methods + results + "widenNumericBoundaries=" + policy.widenNumericBoundaries + "\n" + owners("widenStringTypes",policy.widenStringTypes) + "codecImport=\"com.histmersion.api.json.NewtypeCodecs.given\"\n}\n"
    Files.writeString(report.resolve("rule.conf"),config)
    val args = List("scala-cli","run","--server=false","-q","-S","2.13.18","--dep","ch.epfl.scala:scalafix-cli_2.13.18:0.14.9","--main-class","scalafix.cli.Cli","--",
      "--config",report.resolve("rule.conf").toString,"--scala-version","3.10.0","--sourceroot",root.toString,"--auto-classpath","--auto-classpath-roots","target/out","--files","modules",
      "--out-from",java.util.regex.Pattern.quote(root.resolve("modules").toString + "/"),"--out-to",java.util.regex.Matcher.quoteReplacement(target.resolve("modules").toString + "/"))
    println("Generating one configured model preview across all modules")
    val code = new ProcessBuilder(args.asJava).redirectErrorStream(true).redirectOutput(report.resolve("generation.log").toFile).start().waitFor()
    unchanged(); require(code == 0,"Preview rewrite failed; inspect generation.log")
    def qualified(s: String): String = {
      if(s.contains("$package.")) {
        val entry = catalogue.find(_.head == s).get
        val packages = source(entry(1)).collect { case p: Pkg => p.ref.syntax }
        require(packages.size == 1, "Review nested package ownership: " + entry(1))
        packages.head + "." + entry(4)
      } else s.replace('/','.').stripSuffix("#").stripSuffix(".")
    }
    val missing = bases.keys.filterNot(n => catalogue.exists(r => qualified(r.head) == n)).toList.sorted
    val objects = catalogue.filter(_(3) == "object")
    val groups = missing.groupBy { name =>
      objects.filter(r => name.startsWith(qualified(r.head) + ".")).sortBy(r => -qualified(r.head).length).headOption.map(_.head)
    }
    val scalaBases = Map("java/lang/String#" -> "String", "scala/Int#" -> "Int", "scala/Long#" -> "Long", "scala/Double#" -> "Double")
    def render(prefix: String, names: List[String], indent: String): String = {
      names.groupBy(n => n.stripPrefix(prefix + ".").takeWhile(_ != '.')).toList.sortBy(_._1).map { case(part,ns) =>
        val full = prefix + "." + part
        if(ns == List(full)) {
          indent + "type " + part + " = " + part + ".T\n" + indent + "object " + part +
            " extends io.github.iltotore.iron.RefinedType[" + scalaBases(bases(full)) + ", io.github.iltotore.iron.constraint.any.Pure]\n"
        } else {
          require(!ns.contains(full), "Conflicting type/namespace definition: " + full)
          indent + "object " + part + ":\n" + render(full,ns,indent + "  ")
        }
      }.mkString("\n")
    }
    groups.toList.sortBy(_._1.getOrElse("")).foreach {
      case (Some(owner),names) =>
        val entry = objects.find(_.head == owner).get
        val relative = root.relativize(Paths.get(entry(1))); val output = target.resolve(relative)
        val text = if(Files.exists(output)) Files.readString(output) else Files.readString(Paths.get(entry(1)))
        val tree = text.parse[Source].get
        def ownerName(o: Defn.Object): String = {
          def parents(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: parents(p))
          (parents(o).reverse.collect { case p: Pkg => p.ref.syntax; case p: Defn.Object => p.name.value } :+ o.name.value).mkString(".")
        }
        val matches = tree.collect { case o: Defn.Object if ownerName(o) == qualified(owner) => o }
        require(matches.size == 1 && matches.head.templ.stats.nonEmpty,"Missing explicit companion insertion point: " + owner)
        val o = matches.head; val last = o.templ.stats.last
        val lineStart = text.lastIndexOf('\n',o.pos.start - 1) + 1
        val indent = text.substring(lineStart,o.pos.start).takeWhile(_ == ' ') + "  "
        val next = text.patch(last.pos.end,"\n\n" + render(qualified(owner),names,indent),0)
        next.parse[Source].get; Files.createDirectories(output.getParent); Files.writeString(output,next)
      case (None,names) =>
        names.groupBy { n =>
          val parts = n.split('.').toList
          val pkg = parts.takeWhile(_.head.isLower).mkString(".")
          require(pkg.startsWith("com.histmersion.api") && pkg.nonEmpty)
          (pkg,parts.drop(pkg.split('.').length).head)
        }.toList.sortBy(_._1).foreach { case ((pkg,top),ns) =>
          val relative = Paths.get("modules/api/src/main/scala",pkg.replace('.','/'),top + ".scala")
          require(!Files.exists(root.resolve(relative)) && !Files.exists(target.resolve(relative)),"New type file collision: " + relative)
          val text = "package " + pkg + "\n\n" + render(pkg,ns,"")
          text.parse[Source].get; val output = target.resolve(relative)
          Files.createDirectories(output.getParent); Files.writeString(output,text)
        }
    }
    unchanged()
    val walk = Files.walk(target)
    val files = try walk.iterator.asScala.filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala")).toList finally walk.close()
    val plan = files.map { path =>
      val relative = target.relativize(path); val original = root.resolve(relative)
      Files.readString(path).parse[Source].get
      List(relative.toString,if(Files.exists(original)) hash(Files.readString(original)) else "new",hash(Files.readString(path))).mkString("\t")
    }.sorted
    Files.write(report.resolve("plan.tsv"),("path\tbefore-sha256\tafter-sha256" :: plan).asJava)
    Files.write(report.resolve("definitions.tsv"),("type\tbase\ttreatment" :: bases.toList.sorted.map { case(n,b) => n + "\t" + b + "\t" + (if(missing.contains(n)) "generated-Pure" else "reuse-existing") }).asJava)
    Files.writeString(report.resolve("status.txt"),"GENERATED_UNCOMPILED\nreviewed-fields=" + review.size + "\nconcepts=" + bases.size + "\nnew-definitions=" + missing.size + "\npreview-files=" + files.size + "\nOriginal sources unchanged. Boundary and caller compilation still required; no repair credit.\n")
    println(Files.readString(report.resolve("status.txt")))
  }
  override def afterComplete(): Unit = {
    if(policy.review.nonEmpty) { reviewedPreview(); return }
    val root = Paths.get("").toAbsolutePath.normalize
    val target = root.resolve("target/pagination-preview-v3")
    val report = root.resolve("refactor-audit/models/pagination/preview-v3")
    require(!Files.exists(target) && !Files.exists(report),"Preserve existing preview")
    Files.createDirectories(target); Files.createDirectories(report)
    def rows(p: java.nio.file.Path) = Files.readAllLines(p).asScala.drop(1).map(_.split("\t",-1).toList).toList
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    val sourceHashes = rows(root.resolve("refactor-audit/models/sources.tsv"))
    sourceHashes.foreach(r => require(hash(Files.readString(Paths.get(r.head))) == r(1),"Stale source inventory"))
    val catalogue = rows(root.resolve("refactor-audit/models/types.tsv"))
    val owners = List("OffsetPage#","library/SearchResult#","library/GeoHitPage#","automation/Goal.List#",
      "catalogue/Attestation.List#","catalogue/EventList#","media/AssetLink.List#","media/MediaList#","research/Article.List#")
      .map("com/histmersion/api/" + _)
    def quote(s: String) = "\"" + s + "\""
    val configs = owners.map { owner =>
      val entries = catalogue.filter(_.head == owner); require(entries.size == 1,"Missing record owner: " + owner)
      val entry = entries.head
      val tree = Files.readString(Paths.get(entry(1))).parse[Source].get
      val definitions = tree.collect { case c: Defn.Class if c.pos.startLine + 1 == entry(2).toInt => c }
      require(definitions.size == 1,"Ambiguous record AST: " + owner)
      val c = definitions.head; require(c.ctor.paramClauses.size == 1)
      val params = c.ctor.paramClauses.head.values.map(_.name.value)
      require(params.contains("total") && params.contains("offset"))
      val fields = params.filter(Set("total","shown","offset","next"))
      val mapping = fields.map(n => quote(n) + " = " + quote("com.histmersion.api." + (if(Set("total","shown")(n)) "RowCount" else "Offset"))).mkString(", ")
      "{ owner = " + quote(owner) + ", parameters = [" + params.map(quote).mkString(", ") + "], fields = {" + mapping + "}, optionalFields = [" + fields.filter(_ == "next").map(quote).mkString + "] }"
    }
    val config = "rules = [\"file:tools/refactor/ModelTypeRewrite.scala\"]\nModelTypeRewrite {\nrecords = [\n" + configs.mkString(",\n") +
      "\n]\nresults = { \"com/histmersion/api/OffsetPage#next().\" = \"com.histmersion.api.Offset\" }\noptionalResults = [\"com/histmersion/api/OffsetPage#next().\"]\n" +
      "scalarBoundaryOwners = [\"com/histmersion/api/OffsetPage#next().\", \"com/histmersion/api/OffsetPage#isAll().\", \"com/histmersion/api/OffsetPage.header().\", \"com/histmersion/api/OffsetPage.footer().\", \"com/histmersion/api/OffsetPage.markdown().\"]\n" +
      "scalarBoundaryCalls = { \"com/histmersion/api/Plural.noun().\" = [0] }\ncodecImport = \"com.histmersion.api.json.NewtypeCodecs.given\"\n}\n"
    Files.writeString(report.resolve("rule.conf"),config)
    List("Offset","RowCount").foreach { name =>
      val source = "package com.histmersion.api\n\nimport io.github.iltotore.iron.*\nimport io.github.iltotore.iron.constraint.any.Pure\n\ntype " + name + " = " + name + ".T\nobject " + name + " extends RefinedType[Int, Pure]\n"
      source.parse[Source].get
      val path = target.resolve("modules/api/src/main/scala/com/histmersion/api/" + name + ".scala")
      require(!Files.exists(root.resolve(root.relativize(target).resolve("modules/api/src/main/scala/com/histmersion/api/" + name + ".scala"))))
      require(!Files.exists(root.resolve("modules/api/src/main/scala/com/histmersion/api/" + name + ".scala")))
      Files.createDirectories(path.getParent); Files.writeString(path,source)
    }
    println("Generating isolated pagination preview across modules")
    val args = List("scala-cli","run","--server=false","-q","-S","2.13.18","--dep","ch.epfl.scala:scalafix-cli_2.13.18:0.14.9","--main-class","scalafix.cli.Cli","--",
      "--config",report.resolve("rule.conf").toString,"--scala-version","3.10.0","--sourceroot",root.toString,"--auto-classpath","--auto-classpath-roots","target/out","--files","modules",
      "--out-from",java.util.regex.Pattern.quote(root.resolve("modules").toString + "/"),"--out-to",java.util.regex.Matcher.quoteReplacement(target.resolve("modules").toString + "/"))
    val code = new ProcessBuilder(args.asJava).redirectErrorStream(true).redirectOutput(report.resolve("generation.log").toFile).start().waitFor()
    sourceHashes.foreach(r => require(hash(Files.readString(Paths.get(r.head))) == r(1),"Application source changed during preview"))
    require(code == 0,"Preview generation failed; inspect generation.log")
    val walk = Files.walk(target)
    val files = try walk.iterator.asScala.filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala")).toList finally walk.close()
    val plan = files.map { p =>
      val relative = target.relativize(p); val original = root.resolve(relative)
      List(relative.toString,if(Files.exists(original)) hash(Files.readString(original)) else "new",hash(Files.readString(p))).mkString("\t")
    }.sorted
    Files.writeString(report.resolve("plan.tsv"),("path\tbefore-sha256\tafter-sha256" :: plan).mkString("\n") + "\n")
    Files.writeString(report.resolve("status.txt"),"GENERATED_UNCOMPILED\n" + files.size + " preview source files; all original source hashes unchanged.\nNine result records plus Pure wire Offset/RowCount definitions. Wider record fields remain proposed propagation candidates, not newly adjudicated violations.\nArithmetic, factory parameter typing, codecs and caller closure still require compilation and repair before application.\n")
    println(Files.readString(report.resolve("status.txt")))
  }
}
