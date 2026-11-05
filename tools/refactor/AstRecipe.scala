import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters._

object AstRecipe {
  case class Edit(path: String = "", before: String = "", after: String = "", count: Int = 1, kind: String = "stat")
  object Edit {
    implicit val surface: generic.Surface[Edit] = generic.deriveSurface[Edit]
    implicit val decoder: ConfDecoder[Edit] = generic.deriveDecoder(Edit()).noTypos
  }
  case class Policy(manifest: String = "", report: String = "", edits: List[Edit] = Nil, creates: Map[String,String] = Map.empty, appendStats: Map[String,String] = Map.empty, relocatedPreview: String = "", appliedPlans: List[String] = Nil,
    definitionRoots: List[String] = Nil, reuse: Map[String,String] = Map.empty, names: Map[String,String] = Map.empty, defer: Map[String,String] = Map.empty, buffers: Map[String,String] = Map.empty, localModels: List[String] = Nil,
    measureTypes: Boolean = false, confirmedTypes: List[String] = Nil, expectedTypeSelectors: List[String] = Nil, memberTypes: Boolean = false, previousTypes: String = "", otherConstructors: Boolean = false,
    signatureBuffers: Boolean = false, bufferHistory: List[String] = Nil, localMemberTypes: Boolean = false, indexNewtypes: Boolean = false,
    expectedNewtypes: List[String] = Nil, expectedOverlap: Map[String,Int] = Map.empty)
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
}
class AstRecipe(val policy: AstRecipe.Policy) extends SyntacticRule("AstRecipe") {
  def this() = this(AstRecipe.Policy())
  override def withConfiguration(c: Configuration): Configured[Rule] = c.conf.getOrElse("AstRecipe")(AstRecipe.Policy()).map(new AstRecipe(_))
  override def afterComplete(): Unit = {
    implicit val dialect: Dialect = dialects.Scala3
    def hash(s: String) = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)).map(b => f"${b & 255}%02x").mkString
    def read(p: String) = Files.readString(Paths.get(p))
    def fragment(s: String): String = s.parse[Source] match {
      case Parsed.Success(t) => t.structure
      case _ => s.parse[Stat].get.structure
    }
    def rows(p: String) = Files.readAllLines(Paths.get(p)).asScala.drop(1).map(_.split("\t",-1)).toList
    val initial = policy.appliedPlans.foldLeft(rows(policy.manifest).map(r => r(0) -> r(1)).toMap) { (before, plan) =>
      val changes = rows(plan)
      require(changes.forall(_.length == 3) && changes.map(_(0)).distinct.size == changes.size)
      changes.foreach(r => require(before.get(r(0)).contains(r(1)), "Patch chain does not match manifest: " + r(0)))
      before ++ changes.map(r => r(0) -> r(2))
    }
    val frozen = if(policy.relocatedPreview.isEmpty) initial else {
      require(policy.manifest == policy.relocatedPreview + "/sources.tsv" && Files.exists(Paths.get(policy.relocatedPreview,"applied.txt")))
      val changes = rows(policy.relocatedPreview + "/plan.tsv").map(r => r(0) -> r(2)).toMap
      val moves = rows(policy.relocatedPreview + "/moves.tsv").map(r => r(0) -> r(1)).toMap
      val mapped = initial.toList.map { case(p,h) => moves.getOrElse(p,p) -> changes.getOrElse(p,h) }
      require(mapped.map(_._1).distinct.size == initial.size)
      mapped.toMap
    }
    frozen.foreach { case(p,h) => require(hash(read(p)) == h, "Stale source: " + p) }
    val out = Paths.get(policy.report)
    require(!Files.exists(out) && (policy.edits.nonEmpty || policy.creates.nonEmpty || policy.appendStats.nonEmpty || policy.definitionRoots.nonEmpty || policy.measureTypes))
    require(policy.appendStats.keySet.subsetOf(policy.edits.map(_.path).toSet), "Append requires a reviewed edit in the same file")
    val evidence = scala.collection.mutable.ListBuffer.empty[List[String]]
    val definitionPaths = frozen.keys.filter(p => policy.definitionRoots.exists(p.startsWith) && p.contains("/src/main/")).toSet
    val selected = policy.edits.groupBy(_.path) ++ definitionPaths.filterNot(policy.edits.map(_.path).contains).map(_ -> List.empty[AstRecipe.Edit])
    val bufferTargets = policy.bufferHistory.flatMap(p => rows(p).filter(r => r.length == 4 && r(2).contains("Type.Name(\"Byte\")")).map(r => r(0) -> r(3))).groupBy(_._1).map { case(p,rs) => p -> rs.map(_._2).distinct }
    val targetPaths = bufferTargets.toList.flatMap { case(p,ts) => ts.map(_ -> p) }.toMap
    val externalBufferRefs = if(targetPaths.isEmpty) Map.empty[String,List[String]] else frozen.keys.toList.flatMap { p =>
      val s = read(p)
      if(!targetPaths.keys.exists(s.contains)) Nil else s.parse[Source].get.collect {
        case t: Type.Select if targetPaths.get(t.syntax).exists(_ != p) => t.syntax -> p
      }
    }.groupBy(_._1).map { case(t,rs) => t -> rs.map(_._2).distinct.sorted }
    val changed = selected.toList.sortBy(_._1).flatMap { case(path, recipes) =>
      require(frozen.contains(path))
      val source = read(path)
      val tree = source.parse[Source].get
      val definitionPatches = if(definitionPaths(path)) {
        val records = ModelDefinitions.patches(tree, source, path, policy.reuse, policy.names, policy.defer, policy.buffers, policy.localModels, policy.otherConstructors, evidence)
        val members = if(policy.memberTypes) MemberDefinitions.patches(tree,source,path,policy.reuse,policy.names,policy.signatureBuffers,policy.localMemberTypes,evidence) else Nil
        val corrections = BufferSignatureRepair.patches(tree,path,bufferTargets.getOrElse(path,Nil),externalBufferRefs,evidence)
        (records ++ members ++ corrections).groupBy(p => (p._1,p._2)).toList.flatMap { case((a,b),ps) =>
          if(a == b) {
            val snippets = ps.map(_._3)
            val combined = if(snippets.size > 1 && snippets.forall(s => s.startsWith("\n\n") && s.trim.startsWith("object "))) {
              val headers = snippets.map(s => s.take(s.indexOf(":\n") + 2))
              require(headers.distinct.size == 1,"Conflicting generated companions")
              headers.head + snippets.map(s => s.drop(s.indexOf(":\n") + 2)).mkString("\n")
            } else snippets.mkString("")
            List((a,b,combined))
          } else ps
        }
      } else Nil
      val patches = definitionPatches ++ recipes.flatMap { recipe =>
        require(Set("stat","type")(recipe.kind))
        val pattern: Tree = if(recipe.kind == "type") recipe.before.parse[Type].get else recipe.before.parse[Stat].get
        val replacementAst = if(recipe.kind == "type") recipe.after.parse[Type].get.structure else fragment(recipe.after)
        val matches = tree.collect { case t if t.structure == pattern.structure => t }
        require(matches.size == recipe.count && recipe.count > 0, "AST count mismatch: " + path + " " + recipe.before)
        matches.map { t =>
          val prefix = source.substring(source.lastIndexOf('\n', t.pos.start - 1) + 1, t.pos.start)
          val indent = prefix.takeWhile(_ == ' ')
          val replacement = recipe.after.linesIterator.zipWithIndex.map { case(line,i) => if(i == 0) line else indent + line }.mkString("\n")
          val end = if(recipe.after.isEmpty) tree.tokens.find(tok => tok.pos.start == t.pos.end && tok.is[Token.LF]).map(_.pos.end).getOrElse(t.pos.end) else t.pos.end
          val start = if(recipe.after.isEmpty && prefix.forall(_ == ' ')) t.pos.start - prefix.length else t.pos.start
          evidence += List(path, (t.pos.startLine + 1).toString, t.structure, replacementAst)
          (start, end, replacement)
        }
      } ++ policy.appendStats.get(path).toList.map { snippet =>
        val appended = snippet.parse[Source].get
        require(appended.stats.nonEmpty)
        evidence += List(path, (tree.pos.endLine + 1).toString, "append-after-source", appended.structure)
        (tree.pos.end, tree.pos.end, "\n\n" + snippet + "\n")
      }
      val ordered = patches.sortBy(_._1)
      require(ordered.sliding(2).forall { case List(a,b) => a._2 <= b._1; case _ => true }, "Overlapping AST patches")
      val next = ordered.reverse.foldLeft(source) { case(s,(a,b,n)) => s.patch(a,n,b-a) }
      next.parse[Source].get
      if(source == next) Nil else List((path, source, next))
    }
    val created = policy.creates.toList.sortBy(_._1).map { case(p,s) =>
      require(!Files.exists(Paths.get(p)) && !policy.edits.exists(_.path == p)); s.parse[Source].get; (p,"",s)
    }
    Files.createDirectories(out)
    if(policy.measureTypes) TypeMeasurement.run(frozen,out,policy.confirmedTypes,policy.expectedTypeSelectors,policy.previousTypes,policy.indexNewtypes,policy.appliedPlans,policy.expectedNewtypes,policy.expectedOverlap)
    (changed ++ created).foreach { case(p,b,n) =>
      val dest = out.resolve("sources").resolve(p); Files.createDirectories(dest.getParent); Files.writeString(dest,n)
      if(b.nonEmpty) { val old = out.resolve("before").resolve(p); Files.createDirectories(old.getParent); Files.writeString(old,b) }
    }
    def escape(s: String) = s.replace("\\","\\\\").replace("\t","\\t").replace("\n","\\n")
    Files.write(out.resolve("shapes.tsv"),("path\tline\tbefore-ast\tafter-ast" :: evidence.toList.map(_.map(escape).mkString("\t"))).asJava)
    Files.write(out.resolve("plan.tsv"),("path\tbefore-sha256\tafter-sha256" :: (changed ++ created).map { case(p,b,n) => List(p,if(b.isEmpty) "new" else hash(b),hash(n)).mkString("\t") }).asJava)
    Files.writeString(out.resolve("status.txt"),"PREVIEW_ONLY\nfiles=" + (changed.size + created.size) + "\nAST transformations scoped to frozen source hashes; definition conversion is not compilation or behavioral repair proof.\n")
    println("Preview: " + (changed.size + created.size) + " files, " + evidence.size + " matched ASTs")
    if(policy.definitionRoots.nonEmpty) {
      val converted = evidence.toList.filter(r => r(2).contains("#") && !r(2).startsWith("UNRESOLVED") && !r(2).startsWith("CORRECTION"))
      val summary = s"Converted primitive leaves: ${converted.size}; models: ${converted.map(_(2).takeWhile(_ != '#')).distinct.size}; blocked primitive leaves: ${evidence.count(_(2).startsWith("UNRESOLVED"))}\n"
      Files.writeString(out.resolve("status.txt"),summary,java.nio.file.StandardOpenOption.APPEND)
      println(summary)
      if(policy.buffers.nonEmpty) Files.writeString(out.resolve("status.txt"),s"Buffer corrections: ${evidence.count(_(2).startsWith("CORRECTION"))}\n",java.nio.file.StandardOpenOption.APPEND)
    }
  }
}

class NewtypeIndex(histories: List[String]) {
  private val definitions = scala.collection.mutable.ListBuffer.empty[List[String]]
  private val references = scala.collection.mutable.ListBuffer.empty[List[String]]
  private val definitionOrdinals = scala.collection.mutable.Map.empty[String,Int]
  private def parents(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: parents(p))
  private def owner(t: Tree): String = parents(t).reverse.collect {
    case p: Pkg => p.ref.syntax
    case o: Defn.Object => o.name.value
    case c: Defn.Class => c.name.value
    case c: Defn.Trait => c.name.value
    case e: Defn.Enum => e.name.value
    case d: Defn.Def => d.name.value
  }.mkString(".")
  private def canonical(s: String): String = s.replace("_root_.","").replace("java.lang.String","String").replace("scala.","")
  def inspect(tree: Source,path: String,module: String,scope: String): Unit = {
    def add(t: Tree,name: String,kind: String,base: String,rule: String,alias: String): Unit = {
      val full = owner(t) + "." + name
      val key = path + "::" + full
      val ordinal = definitionOrdinals.getOrElse(key,0); definitionOrdinals(key) = ordinal + 1
      definitions += List(key + "#" + ordinal,path,module,scope,(t.pos.startLine + 1).toString,full,name,kind,base,rule,
        canonical(base),rule.stripPrefix("_root_."),if(parents(t).exists(_.is[Defn.Def])) "local" else "member",alias,t.structure)
    }
    tree.collect {
      case o: Defn.Object => o.templ.inits.foreach { init => init.tpe match {
        case a: Type.Apply if Set("RefinedType","RefinedSubtype")(a.tpe.syntax.split('.').last) && a.argClause.values.size == 2 =>
          val linked = o.parent.toList.flatMap(_.children).exists {
            case d: Defn.Type => d.name.value == o.name.value && d.body.syntax == o.name.value + ".T"
            case _ => false
          }
          add(o,o.name.value,"Iron-parent-shape",a.argClause.values.head.syntax,a.argClause.values(1).syntax,if(linked) "alias-present" else "alias-unresolved")
        case _ => ()
      }}
      case t: Defn.Type if t.mods.exists(_.is[Mod.Opaque]) => add(t,t.name.value,"opaque",t.body.syntax,"opaque-bound-unresolved","opaque")
    }
  }
  def use(t: Type,path: String,module: String,scope: String,line: String,context: String,kind: String): Unit = t.collect {
    case s: Type.Select if !s.syntax.endsWith(".T") => references += List(path,module,scope,line,context,kind,s.syntax.stripPrefix("_root_."))
    case n: Type.Name if !n.parent.exists(_.is[Type.Select]) => references += List(path,module,scope,line,context,kind,n.value)
  }
  def finish(out: java.nio.file.Path, expected: List[String], expectedGroups: Map[String,Int]): Unit = {
    def escape(s: String) = s.replace("\\","\\\\").replace("\t","\\t").replace("\n","\\n").replace("\r","\\r")
    def write(name: String,header: String,rows: List[List[String]]): Unit = Files.write(out.resolve(name),(header :: rows.map(_.map(escape).mkString("\t"))).asJava)
    val ds = definitions.toList.sortBy(r => (r(1),r(5)))
    require(ds.map(_.head).distinct.size == ds.size,"Duplicate newtype index identity")
    if(expected.nonEmpty) require(ds.map(_(5)).sorted == expected.sorted,"Newtype fixture definition mismatch")
    val history = histories.flatMap { p =>
      val file = Paths.get(p).getParent.resolve("shapes.tsv")
      if(!Files.exists(file)) Nil else Files.readAllLines(file).asScala.drop(1).flatMap { line =>
        val r = line.split("\t",-1)
        if(r.length != 4 || !r(2).contains("#") || r(2).startsWith("CORRECTION") || r(2).startsWith("UNRESOLVED") || !r(3).matches("[A-Za-z_][A-Za-z0-9_.]*")) Nil
        else {
          val parts = r(2).split("#",2)
          val target = if(r(3).startsWith("com.")) r(3) else parts.head + "." + r(3).split('.').last
          List(List(r(0),target,parts(1).takeWhile(_ != '/'),file.toString))
        }
      }.toList
    }.distinct
    val historyByOwner = history.groupBy(r => (r.head,r(1)))
    val historyByName = history.groupBy(r => (r.head,r(1).split('.').last))
    val origin = ds.flatMap { d =>
      val exact = historyByOwner.getOrElse((d(1),d(5)),Nil)
      val fallback = historyByName.getOrElse((d(1),d(6)),Nil)
      val selected = if(exact.nonEmpty) exact else if(fallback.map(_(1)).distinct.size == 1) fallback else Nil
      selected.map(r => List(d.head,r(2),r(3),if(exact.nonEmpty) "owner-match" else "unique-source-name; review"))
    }
    val byFull = ds.groupBy(_(5))
    val byName = ds.groupBy(_(6))
    val uses = references.toList.distinct.flatMap { r =>
      val raw = r(6)
      if(!byName.contains(raw.split('.').last)) Nil else {
        val prefixes = r(4).split('.').toList.inits.map(_.mkString(".")).toList
        val matches = (raw :: prefixes.map(p => if(p.isEmpty) raw else p + "." + raw)).distinct.iterator.map(k => byFull.getOrElse(k,Nil)).find(_.nonEmpty).getOrElse(Nil)
        if(matches.size == 1) List(r ++ List(matches.head.head,if(raw == matches.head(5)) "fully-qualified-AST-match" else "lexical-AST-match; imports-unverified"))
        else List(r ++ List("UNRESOLVED","import-or-owner-ambiguity"))
      }
    }
    val byOrigin = origin.groupBy(_.head)
    val members = ds.filter(_(3) == "production").flatMap { d =>
      val tokens = byOrigin.getOrElse(d.head,Nil).map(_(1).split('.').last).filter(_.nonEmpty).distinct
      (if(tokens.nonEmpty) tokens else List(d(6))).map { token =>
        val purpose = token.toLowerCase(java.util.Locale.ROOT)
        (purpose,d(10),d)
      }
    }.groupBy(x => (x._1,x._2)).toList.filter(_._2.map(_._3.head).distinct.size > 1)
    val generic = Set("id","name","value","key","result","wire","label","text","kind","n","s","x","y")
    val priority = Set("slug","citekey","title","year","seconds","bytes","offset","limit","pagesize","sha256","doi","budgetusd","tokens","lat","lon")
    val membership = scala.collection.mutable.ListBuffer.empty[List[String]]
    val groups = members.map { case((purpose,base),rs) =>
      val selected = rs.map(_._3).groupBy(_.head).values.map(_.head).toList.sortBy(_.head)
      val key = purpose + "|" + base
      val id = java.security.MessageDigest.getInstance("SHA-256").digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)).take(10).map(b => f"${b & 255}%02x").mkString
      selected.foreach(d => membership += List(id,d.head,purpose,"candidate-only; do not merge automatically"))
      val constraints = selected.map(_(11)).distinct.sorted
      val modules = selected.map(_(2)).distinct.sorted
      val score = (if(priority(purpose)) 100 else if(generic(purpose)) 0 else 20) + math.min(selected.size,50)
      List(id,purpose,base,selected.size.toString,constraints.size.toString,modules.mkString(";"),constraints.mkString(";"),
        if(constraints.size > 1) "constraint-syntax-diff; resolution-and-construction-review" else "same-syntax; semantic-identity-unverified",
        if(modules.size > 1) "cross-module-and-layer-review" else "owner-purpose-review",score.toString)
    }.sortBy(r => (-r(9).toInt,r(1),r(2)))
    expectedGroups.foreach { case(p,n) => require(membership.filter(_(2) == p).map(_(1)).distinct.size == n,"Overlap fixture membership differs: " + p) }
    write("newtypes.tsv","id\tpath\tmodule\tscope\tline\tlexical-name\tname\tkind\tbase\tconstraint\tbase-bucket\tconstraint-syntax\tlocality\talias\tast",ds)
    write("newtype-origins.tsv","definition\tdeclaration-origin\tevidence\tmatching",origin)
    write("newtype-uses.tsv","path\tmodule\tscope\tline\tcontext\tkind\tsyntax\tdefinition\tmatching",uses)
    write("overlap-groups.tsv","group\tpurpose-token\tbase-bucket\tdefinitions\tconstraint-variants\tmodules\tconstraints\tconstraint-review\tboundary-review\tpriority",groups)
    write("overlap-members.tsv","group\tdefinition\tpurpose-token\tstatus",membership.toList)
    write("newtype-counts.tsv","scope\tmodule\tkind\tdefinitions",ds.groupBy(r => (r(3),r(2),r(7))).toList.sortBy(_._1).map { case((s,m,k),rs) => List(s,m,k,rs.size.toString) })
    val note = s"${ds.size} newtype-shaped definitions (${ds.count(_(3) == "production")} production, ${ds.count(_(3) == "test")} test); ${groups.size} production overlap candidate groups; ${uses.count(_(7) == "UNRESOLVED")} unresolved candidate references.\nGrouping is a review queue, not semantic equivalence. Raw syntax constraints, owner meaning and module isolation must be reviewed. Imports, aliases, inherited newtypes and external symbols are not resolved. Reference counts cover measured declaration types only.\n"
    Files.writeString(out.resolve("newtype-index.txt"),note)
    println(note)
  }
}

object TypeMeasurement {
  def run(sources: Map[String,String], out: java.nio.file.Path, confirmed: List[String], expected: List[String], previous: String, indexTypes: Boolean, histories: List[String], expectedNewtypes: List[String], expectedOverlap: Map[String,Int]): Unit = {
    import scala.jdk.CollectionConverters._
    implicit val dialect: Dialect = dialects.Scala3
    val primitives = Set("String","Boolean","Byte","Short","Int","Long","Float","Double","Char")
    val positions = scala.collection.mutable.ListBuffer.empty[List[String]]
    val leaves = scala.collection.mutable.ListBuffer.empty[List[String]]
    val contexts = scala.collection.mutable.Map.empty[String,List[String]]
    val index = if(indexTypes) Some(new NewtypeIndex(histories)) else None
    def parents(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: parents(p))
    def owner(t: Tree): String = parents(t).reverse.collect {
      case p: Pkg => p.ref.syntax
      case c: Defn.Class => c.name.value
      case c: Defn.Trait => c.name.value
      case c: Defn.Object => c.name.value
      case c: Defn.Enum => c.name.value
      case d: Defn.Def if !(d.name eq t) => d.name.value
      case d: Decl.Def if !(d.name eq t) => d.name.value
    }.mkString(".")
    sources.toList.sortBy(_._1).foreach { case(path,_) =>
      val source = Files.readString(Paths.get(path))
      val tree = source.parse[Source].get
      val scope = if(path.contains("/src/test/")) "test" else "production"
      val module = path.stripPrefix("modules/").split("/src/").head
      index.foreach(_.inspect(tree,path,module,scope))
      val serial = scala.collection.mutable.Map.empty[String,Int]
      def position(n: Name, kind: String, declared: Option[Type]): Unit = {
        val o = owner(n)
        val key = o + "." + n.value + ":" + kind
        val ordinal = serial.getOrElse(key,0); serial(key) = ordinal + 1
        val id = path + "::" + key + "#" + ordinal
        val methods = parents(n).collect { case d: Defn.Def => d }
        val local = kind == "lambda-parameter" || methods.exists(d => !(d.name eq n) &&
          !(kind == "method-parameter" && methods.headOption.contains(d)))
        positions += List(id,path,module,scope,if(local) "local" else "member",(n.pos.startLine + 1).toString,kind,o,n.value,declared.fold("<inferred>")(_.syntax),declared.fold("")(_.structure))
        val ctx = parents(n)
        val mods = ctx.flatMap {
          case d: Defn.Def => d.mods
          case d: Defn.Val => d.mods
          case d: Defn.Var => d.mods
          case d: Defn.Class => d.mods
          case d: Defn.Trait => d.mods
          case d: Defn.Object => d.mods
          case _ => Nil
        }
        val native = mods.exists { case a: Mod.Annot => a.init.tpe.syntax.endsWith("native"); case _ => false } || ctx.exists {
          case d: Defn.Def => d.body match { case Term.Select(_,Term.Name("native")) => true; case _ => false }
          case _ => false
        }
        val category = if(native) "native-contract-review" else if(mods.exists(_.is[Mod.Override])) "override-contract-review"
          else if(kind == "lambda-parameter") "lambda-propagation-review"
          else if(kind == "alias") "alias-signature-review"
          else if(declared.exists(_.collect { case _: Type.ByName => true; case _: Type.Repeated => true; case _: Type.ApplyInfix => true }.nonEmpty)) "wrapper-shape-review"
          else if(declared.exists(_.collect { case a: Type.Apply if a.tpe.syntax.endsWith("Array") => true }.nonEmpty)) "buffer-or-native-array-review"
          else "declaration-owner-or-name-review"
        val treatment = if(Set("wrapper-shape-review","alias-signature-review")(category)) "bulk-rewrite-candidate"
          else if(category == "lambda-propagation-review") "type-propagation-candidate" else "targeted-or-design-review"
        contexts(id) = List(category,treatment,"candidate; no accepted exception inferred")
        declared.foreach(t => index.foreach(_.use(t,path,module,scope,(n.pos.startLine + 1).toString,o,kind)))
        def emit(route: String,status: String,detail: String,t: Option[Type],callback: Boolean): Unit = {
          val selector = key + "|" + route
          leaves += List(id,route,status,detail,t.fold("")(_.structure),if(callback) "callback" else kind,
            if(status == "primitive" && confirmed.contains(selector)) "confirmed" else if(status == "primitive") "candidate" else "unresolved",selector)
        }
        def inspect(t: Type, route: String, callback: Boolean = false): Unit = t match {
          case Type.Name(n) if primitives(n) => emit(route,"primitive",n,Some(t),callback)
          case Type.Select(Term.Name("scala"),Type.Name(n)) if primitives(n) => emit(route,"primitive",n,Some(t),callback)
          case Type.Select(Term.Select(Term.Name("java"),Term.Name("lang")),Type.Name("String")) => emit(route,"primitive","String",Some(t),callback)
          case a: Type.Apply => a.argClause.values.zipWithIndex.foreach { case(v,i) => inspect(v,route + "/argument[" + i + "]",callback) }
          case f: Type.Function =>
            f.paramClause.values.zipWithIndex.foreach { case(v,i) => inspect(v,route + "/function-parameter[" + i + "]",true) }
            inspect(f.res,route + "/function-result",true)
          case f: Type.ContextFunction =>
            f.paramClause.values.zipWithIndex.foreach { case(v,i) => inspect(v,route + "/context-parameter[" + i + "]",true) }
            inspect(f.res,route + "/context-result",true)
          case t: Type.Tuple => t.args.zipWithIndex.foreach { case(v,i) => inspect(v,route + "/tuple[" + i + "]",callback) }
          case t: Type.ByName => inspect(t.tpe,route + "/by-name",callback)
          case t: Type.Repeated => inspect(t.tpe,route + "/repeated",callback)
          case t: Type.ApplyInfix => inspect(t.lhs,route + "/left",callback); inspect(t.rhs,route + "/right",callback)
          case Type.Name(n) if Set("Unit","Nothing","Any","AnyRef","Null")(n) => ()
          case _ => emit(route,"unresolved",t.syntax,Some(t),callback)
        }
        declared match {
          case Some(t) => inspect(t,"type")
          case None => emit("type","inferred-unresolved","No current semantic type available",None,false)
        }
      }
      tree.collect {
        case p: Term.Param =>
          val context = parents(p)
          val kind = context.collectFirst {
            case _: Term.Function => "lambda-parameter"
            case _: Term.ContextFunction => "lambda-parameter"
            case _: Ctor.Primary => "constructor-parameter"
            case _: Defn.Def => "method-parameter"
            case _: Decl.Def => "method-parameter"
          }.getOrElse("parameter")
          position(p.name,kind,p.decltpe)
        case d: Defn.Def => position(d.name,"method-result",d.decltpe)
        case d: Decl.Def => position(d.name,"method-result",Some(d.decltpe))
        case v: Defn.Val => v.pats.flatMap(_.collect { case p: Pat.Var => p.name }).foreach(position(_,"value",v.decltpe))
        case v: Defn.Var => v.pats.flatMap(_.collect { case p: Pat.Var => p.name }).foreach(position(_,"variable",v.decltpe))
        case v: Decl.Val => v.pats.flatMap(_.collect { case p: Pat.Var => p.name }).foreach(position(_,"value",Some(v.decltpe)))
        case v: Decl.Var => v.pats.flatMap(_.collect { case p: Pat.Var => p.name }).foreach(position(_,"variable",Some(v.decltpe)))
        case t: Defn.Type if !t.mods.exists(_.is[Mod.Opaque]) => position(t.name,"alias",Some(t.body))
      }
    }
    val byId = positions.map(p => p.head -> p).toMap
    require(byId.size == positions.size,"Duplicate measurement identities")
    val observed = leaves.filter(_(2) == "primitive").map(_(7)).toSet
    require(confirmed.forall(observed),"Confirmed measurement selector missing: " + confirmed.filterNot(observed).mkString(", "))
    if(expected.nonEmpty) require(leaves.filter(_(2) == "primitive").map(_(7)).toList.sorted == expected.sorted,"Exact primitive selector fixture mismatch")
    def esc(s: String) = s.replace("\\","\\\\").replace("\t","\\t").replace("\r","\\r").replace("\n","\\n")
    def write(name: String,header: String,rows: List[List[String]]): Unit = Files.write(out.resolve(name),(header :: rows.map(_.map(esc).mkString("\t"))).asJava)
    write("positions.tsv","id\tpath\tmodule\tscope\tlocality\tline\tkind\towner\tname\tdeclared\tast",positions.toList)
    write("leaves.tsv","position\troute\tstatus\tdetail\tast\tfamily\tclassification\tselector",leaves.toList)
    val residual = leaves.toList.filter(l => l(2) == "primitive" && byId(l.head)(3) == "production").map { l =>
      val p = byId(l.head)
      List(l.head,l(1),p(1),p(5),p(2),p(6),p(7),p(8),l(3)) ++ contexts(l.head)
    }
    write("residual-treatment.tsv","position\troute\tpath\tline\tmodule\tkind\towner\tname\tprimitive\tcategory\tproposed-treatment\tstatus",residual)
    write("residual-treatment-counts.tsv","category\tproposed-treatment\tleaves",residual.groupBy(r => (r(9),r(10))).toList.sortBy(_._1).map { case((c,t),rs) => List(c,t,rs.size.toString) })
    write("residual-treatment-samples.tsv","position\troute\tpath\tline\tmodule\tkind\towner\tname\tprimitive\tcategory\tproposed-treatment\tstatus",residual.groupBy(_(9)).toList.sortBy(_._1).flatMap { case(_,rs) => rs.groupBy(_(4)).toList.sortBy(_._1).take(2).map(_._2.head) })
    index.foreach(_.finish(out,expectedNewtypes,expectedOverlap))
    write("sources.tsv","path\tsha256",sources.toList.sorted.map { case(p,h) => List(p,h) })
    if(sources.keys.forall(_.startsWith("modules/"))) {
      val stream = Files.walk(Paths.get("modules"))
      val current = try stream.iterator.asScala.filter(Files.isRegularFile(_)).map(_.toString).filter(p =>
        p.endsWith(".scala") && (p.contains("/src/main/") || p.contains("/src/test/")) &&
          !p.contains("/target/") && !p.contains("/.scala-build/")).toSet finally stream.close()
      write("coverage.tsv","path\tstatus",(current ++ sources.keySet).toList.sorted.map(p => List(p,
        if(!current(p)) "manifest-only" else if(!sources.contains(p)) "UNMEASURED" else "measured")))
      require(current == sources.keySet,"Current source coverage differs; see coverage.tsv (no completion claim)")
    }
    val groups = leaves.toList.groupBy(l => (byId(l.head)(3),byId(l.head)(2),byId(l.head)(4),l(5),l(2),l(6))).toList.sortBy(_._1)
    write("counts.tsv","scope\tmodule\tlocality\tfamily\tstatus\tclassification\tpositions\tleaves",groups.map { case((s,m,o,f,t,c),ls) => List(s,m,o,f,t,c,ls.map(_.head).distinct.size.toString,ls.size.toString) })
    val summary = leaves.toList.groupBy(l => (byId(l.head)(3),byId(l.head)(4),l(5),l(2),l(6))).toList.sortBy(_._1)
    write("totals.tsv","scope\tlocality\tfamily\tstatus\tclassification\tpositions\tleaves",summary.map { case((s,o,f,t,c),ls) => List(s,o,f,t,c,ls.map(_.head).distinct.size.toString,ls.size.toString) })
    if(previous.nonEmpty) {
      val before = Files.readAllLines(Paths.get(previous,"leaves.tsv")).asScala.drop(1).map(_.split("\t",-1)).filter(_(2) == "primitive").map(r => r(0) + "|" + r(1)).toSet
      val after = leaves.filter(_(2) == "primitive").map(r => esc(r.head) + "|" + esc(r(1))).toSet
      val delta = before.diff(after).toList.sorted.map(id => List("raw-primitive-eliminated-unverified",id)) ++ after.diff(before).toList.sorted.map(id => List("new-primitive",id))
      write("changes.tsv","change\tstable-position-and-route",delta)
      println(s"Stable-ID delta: ${before.diff(after).size} raw leaves eliminated; ${after.diff(before).size} new primitive leaves")
    }
    Files.writeString(out.resolve("measurement.txt"),s"${sources.size} files; ${positions.size} positions; ${leaves.count(_(2) == "primitive")} explicit primitive leaves; ${leaves.count(_(6) == "confirmed")} confirmed occurrences.\nAST-only: inference, aliases, named references and primitive shadowing require semantic review. Candidates are not confirmed violations; no accepted exceptions inferred. IDs omit line numbers; same-name overloads use source-order ordinals. Historical semantic inventory remains unchanged.\n")
    println(Files.readString(out.resolve("measurement.txt")))
  }
}

object BufferSignatureRepair {
  def patches(tree: Source, path: String, targets: List[String], external: Map[String,List[String]], evidence: scala.collection.mutable.ListBuffer[List[String]]): List[(Int,Int,String)] = {
    def parents(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: parents(p))
    def qualified(o: Defn.Object): String = (parents(o).reverse :+ o).collect {
      case p: Pkg => p.ref.syntax
      case o: Defn.Object => o.name.value
      case c: Defn.Class => c.name.value
      case c: Defn.Trait => c.name.value
      case c: Defn.Enum => c.name.value
    }.mkString(".")
    targets.flatMap { target =>
      val candidates = tree.collect { case o: Defn.Object if qualified(o) == target => o }
      require(candidates.size == 1,"Historical buffer newtype owner changed: " + target)
      val o = candidates.head
      require(o.templ.inits.size == 1 && o.templ.stats.isEmpty,"Historical buffer newtype changed: " + target)
      val parent = o.templ.inits.head.tpe.asInstanceOf[Type.Apply]
      require(parent.tpe.syntax.endsWith("RefinedType") && parent.argClause.values.size == 2 && parent.argClause.values(1).syntax.endsWith(".Pure"))
      val base = parent.argClause.values.head
      if(base.syntax == "_root_.scala.Array[_root_.scala.Byte]") Nil
      else {
        require(base.syntax == "_root_.scala.Byte","Historical buffer base differs: " + target)
        val refs = tree.collect { case t: Type.Select if t.syntax == target => t }
        val arrays = tree.collect { case a: Type.Apply if Set("Array","scala.Array","_root_.scala.Array")(a.tpe.syntax) &&
          a.argClause.values.size == 1 && a.argClause.values.head.syntax == target => a }
        if(refs.nonEmpty && refs.size == arrays.size && !external.contains(target)) {
          val edits = arrays.map { a =>
            evidence += List(path,(a.pos.startLine + 1).toString,"CORRECTION " + target + " " + a.structure,target)
            (a.pos.start,a.pos.end,target)
          }
          edits :+ ((base.pos.start,base.pos.end,"_root_.scala.Array[_root_.scala.Byte]"))
        } else {
          evidence += List(path,(o.pos.startLine + 1).toString,"UNRESOLVED buffer correction " + target,"Non-array, mixed, absent or cross-file references; primitive newtype preserved. " + external.getOrElse(target,Nil).mkString(","))
          Nil
        }
      }
    }.distinct
  }
}

object MemberDefinitions {
  def patches(tree: Source, source: String, path: String, reuse: Map[String,String], names: Map[String,String], wholeBuffers: Boolean, localMembers: Boolean,
      evidence: scala.collection.mutable.ListBuffer[List[String]]): List[(Int,Int,String)] = {
    val result = scala.collection.mutable.ListBuffer.empty[(Int,Int,String)]
    val additions = scala.collection.mutable.LinkedHashMap.empty[Tree,scala.collection.mutable.LinkedHashMap[String,String]]
    val localDeclarations = scala.collection.mutable.Map.empty[(Tree,String),Int]
    def parents(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: parents(p))
    def indent(t: Tree): String = source.substring(source.lastIndexOf('\n',t.pos.start - 1) + 1,t.pos.start).takeWhile(_ == ' ')
    def upper(s: String): String = if(s.isEmpty) "Anonymous" else s.head.toUpper + s.tail
    def ownerName(t: Tree): String = (parents(t).reverse :+ t).collect {
      case p: Pkg => p.ref.syntax
      case o: Defn.Object => o.name.value
      case c: Defn.Class => c.name.value
      case c: Defn.Trait => c.name.value
      case c: Defn.Enum => c.name.value
    }.mkString(".")
    def stats(t: Tree): List[Stat] = t match {
      case o: Defn.Object => o.templ.stats
      case _ => Nil
    }
    def leaves(t: Type,suffix: String = ""): List[(Type,String,String)] = t match {
      case Type.Name(n) if Set("String","Int","Long","Double","Boolean","Float","Short","Byte","Char")(n) => List((t,n,suffix))
      case a: Type.Apply if wholeBuffers && Set("Array","scala.Array","_root_.scala.Array")(a.tpe.syntax) &&
        a.argClause.values.size == 1 && Set("Byte","Int")(a.argClause.values.head.syntax) => List((a,"Array[" + a.argClause.values.head.syntax + "]",suffix))
      case a: Type.Apply => a.argClause.values.zipWithIndex.flatMap { case(v,i) => leaves(v,suffix + (if(a.argClause.values.size == 1) "" else "Part" + (i + 1))) }
      case f: Type.Function => f.paramClause.values.zipWithIndex.flatMap { case(v,i) => leaves(v,suffix + "Arg" + (i + 1)) } ++ leaves(f.res,suffix + "Result")
      case f: Type.ContextFunction => f.paramClause.values.zipWithIndex.flatMap { case(v,i) => leaves(v,suffix + "ContextArg" + (i + 1)) } ++ leaves(f.res,suffix + "Result")
      case t: Type.Tuple => t.args.zipWithIndex.flatMap { case(v,i) => leaves(v,suffix + "Part" + (i + 1)) }
      case _ => Nil
    }
    def rewrite(declaration: Tree, keyName: String, prefix: String, t: Type, blocked: Boolean): Unit = {
      val context = parents(declaration)
      val enclosing = context.collectFirst { case o: Defn.Object => o; case c: Defn.Class => c; case c: Defn.Trait => c; case c: Defn.Enum => c }
      enclosing.foreach { entity =>
        val owner = ownerName(entity)
        val enclosingMethods = context.takeWhile(_ ne entity).reverse.collect { case d: Defn.Def => d.name.value }
        val local = enclosingMethods.nonEmpty
        val localOwner = parents(entity).exists(t => t.is[Defn.Def] || t.is[Term.Block])
        val targetOwner: Tree = entity match {
          case o: Defn.Object => o
          case _ => entity.parent.toList.flatMap(_.children).collectFirst {
            case o: Defn.Object if o.name.value == owner.split('.').last => o
          }.getOrElse(entity)
        }
        val occupied = stats(targetOwner).flatMap {
          case d: Defn.Type => List(d.name.value)
          case d: Defn.Object => List(d.name.value)
          case d: Defn.Class => List(d.name.value)
          case d: Defn.Trait => List(d.name.value)
          case d: Defn.Enum => List(d.name.value)
          case _ => Nil
        }.toSet
        leaves(t).foreach { case(leaf,base,suffix) =>
          val scopedKey = (enclosingMethods :+ keyName).mkString(".")
          val key = owner + "." + scopedKey + (if(suffix.isEmpty) "" else "." + suffix)
          val name = names.getOrElse(key,enclosingMethods.map(upper).mkString + prefix + suffix)
          val mappings = reuse.toList.collect { case(pattern,value) if key.matches(pattern.split("\\*",-1).map(java.util.regex.Pattern.quote).mkString(".*")) => value }.distinct
          require(mappings.size <= 1,"Conflicting member concept mappings: " + key)
          val declared = additions.getOrElseUpdate(targetOwner,scala.collection.mutable.LinkedHashMap.empty[String,String])
          val shadowed = tree.collect { case d: Defn.Type if d.name.value == base => d }.nonEmpty
          val localCollision = local && localDeclarations.get((targetOwner,name)).exists(_ != declaration.pos.start)
          val packed = !base.startsWith("Array[") && (leaf :: parents(leaf).takeWhile(x => x.pos.start >= t.pos.start && x.pos.end <= t.pos.end)).exists {
            case a: Type.Apply => Set("Array","scala.Array","_root_.scala.Array","js.Array")(a.tpe.syntax)
            case _ => false
          }
          val empty = targetOwner.is[Defn.Object] && stats(targetOwner).isEmpty
          if(blocked || (local && !localMembers) || localOwner || localCollision || shadowed || packed || empty || !name.matches("[A-Z][A-Za-z0-9_]*") ||
            (mappings.isEmpty && (occupied(name) || declared.get(name).exists(_ != base)))) {
            val reasons = List(if(blocked) "override-or-native-contract" else "",if(local && !localMembers) "local" else "",if(localOwner) "local-owner-requires-local-companion" else "",if(localCollision) "local-declaration-name-conflict" else "",if(shadowed) "primitive-name-shadowing" else "",if(packed) "whole-buffer-modeling-required" else "",if(empty) "empty-companion" else "").filter(_.nonEmpty)
            evidence += List(path,(leaf.pos.startLine + 1).toString,"UNRESOLVED member " + key,leaf.structure + " " + (if(reasons.isEmpty) "name-or-base-conflict" else reasons.mkString(",")))
          } else {
            val replacement = mappings.headOption.getOrElse(owner + "." + name)
            if(mappings.isEmpty) declared(name) = base
            if(local) localDeclarations((targetOwner,name)) = declaration.pos.start
            result += ((leaf.pos.start,leaf.pos.end,replacement))
            evidence += List(path,(leaf.pos.startLine + 1).toString,owner + "#" + scopedKey + "/" + suffix + " " + leaf.structure,replacement)
          }
        }
      }
    }
    tree.collect {
      case d: Defn.Def =>
        val blocked = d.mods.exists(_.is[Mod.Override]) || (d.body match { case Term.Select(_,Term.Name("native")) => true; case _ => false })
        d.decltpe.foreach(rewrite(d,d.name.value,upper(d.name.value) + "Result",_,blocked))
        d.paramClauseGroups.flatMap(_.paramClauses).flatMap(_.values).foreach(p => p.decltpe.foreach(rewrite(d,d.name.value + "." + p.name.value,upper(d.name.value) + upper(p.name.value),_,blocked)))
      case d: Decl.Def =>
        rewrite(d,d.name.value,upper(d.name.value) + "Result",d.decltpe,false)
        d.paramClauseGroups.flatMap(_.paramClauses).flatMap(_.values).foreach(p => p.decltpe.foreach(rewrite(d,d.name.value + "." + p.name.value,upper(d.name.value) + upper(p.name.value),_,false)))
      case v: Defn.Val => v.pats.collect { case Pat.Var(n) => n }.foreach(n => v.decltpe.foreach(rewrite(v,n.value,upper(n.value),_,false)))
      case v: Defn.Var => v.pats.collect { case Pat.Var(n) => n }.foreach(n => v.decltpe.foreach(rewrite(v,n.value,upper(n.value),_,false)))
    }
    additions.foreach { case(target,declared) => if(declared.nonEmpty) {
      val own = ownerName(target).split('.').last
      val pad = target match { case o: Defn.Object if o.templ.stats.nonEmpty => indent(o.templ.stats.head); case _ => indent(target) + "  " }
      val definitions = declared.toList.flatMap { case(n,b) => List("type " + n + " = " + n + ".T",
        "object " + n + " extends _root_.io.github.iltotore.iron.RefinedType[" + (if(b == "String") "_root_.java.lang.String" else if(b.startsWith("Array[")) "_root_.scala.Array[_root_.scala." + b.stripPrefix("Array[") else "_root_.scala." + b) + ", _root_.io.github.iltotore.iron.constraint.any.Pure]") }.mkString("\n" + pad)
      target match {
        case o: Defn.Object if o.templ.stats.nonEmpty => val at = o.templ.stats.head.pos.start; result += ((at,at,definitions + "\n\n" + pad))
        case _: Defn.Object => sys.error("Empty companion requires explicit treatment: " + own)
        case _ => result += ((target.pos.end,target.pos.end,"\n\n" + indent(target) + "object " + own + ":\n" + pad + definitions))
      }
    }}
    result.toList
  }
}

object ModelDefinitions {
  def patches(tree: Source, source: String, path: String, reuse: Map[String,String], names: Map[String,String], defer: Map[String,String], buffers: Map[String,String], localModels: List[String], otherConstructors: Boolean,
      evidence: scala.collection.mutable.ListBuffer[List[String]]): List[(Int,Int,String)] = {
    val primitive = Set("String", "Boolean", "Byte", "Short", "Int", "Long", "Float", "Double", "Char")
    val result = scala.collection.mutable.ListBuffer.empty[(Int,Int,String)]
    def parents(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: parents(p))
    def indent(t: Tree): String = source.substring(source.lastIndexOf('\n',t.pos.start - 1) + 1,t.pos.start).takeWhile(_ == ' ')
    def upper(s: String): String = if(s.isEmpty) "Anonymous" else s.head.toUpper + s.tail
    def qualified(b: String): String = if(b == "String") "_root_.java.lang.String" else "_root_.scala." + b
    def base(t: Type): Option[String] = t match {
      case Type.Name(n) if primitive(n) => Some(n)
      case Type.Select(Term.Name("scala"),Type.Name(n)) if primitive(n) => Some(n)
      case _ => None
    }
    def leaves(t: Type, suffix: String = ""): List[(Type,String,String)] = base(t).map(b => List((t,b,suffix))).getOrElse(t match {
      case a: Type.Apply => a.argClause.values.zipWithIndex.flatMap { case(v,i) =>
        val role = if(a.argClause.values.size == 1) "" else if(a.tpe.syntax.endsWith("Map")) { if(i == 0) "Key" else "Value" } else "Part" + (i + 1)
        leaves(v,suffix + role)
      }
      case t: Type.Tuple => t.args.zipWithIndex.flatMap { case(v,i) => leaves(v,suffix + "Part" + (i + 1)) }
      case f: Type.Function => f.paramClause.values.zipWithIndex.flatMap { case(v,i) => leaves(v,suffix + "Arg" + (i + 1)) } ++ leaves(f.res,suffix + "Result")
      case f: Type.ContextFunction => f.paramClause.values.zipWithIndex.flatMap { case(v,i) => leaves(v,suffix + "ContextArg" + (i + 1)) } ++ leaves(f.res,suffix + "Result")
      case _ => Nil
    })
    tree.collect {
      case c: Defn.Class if c.mods.exists(_.is[Mod.Case]) || otherConstructors => (c: Tree,c.name,c.ctor)
      case e: Defn.Enum if otherConstructors => (e: Tree,e.name,e.ctor)
      case e: Defn.EnumCase if otherConstructors => (e: Tree,e.name,e.ctor)
    }.foreach { case(c,className,ctor) =>
      val ancestry = parents(c)
      val owner = (ancestry.reverse.collect {
        case p: Pkg => p.ref.syntax
        case o: Defn.Object => o.name.value
        case d: Defn.Class => d.name.value
        case d: Defn.Trait => d.name.value
        case d: Defn.Enum => d.name.value
      } :+ className.value).mkString(".")
      val enclosingEnum = if(c.is[Defn.EnumCase]) ancestry.collectFirst { case e: Defn.Enum => e } else None
      val referenceOwner = enclosingEnum.map(_.name.value + ".").getOrElse("") + className.value
      val enumCompanion = enclosingEnum.flatMap(e => e.parent.toList.flatMap(_.children).collectFirst { case o: Defn.Object if o.name.value == e.name.value => o })
      val siblings = if(enclosingEnum.nonEmpty) enumCompanion.toList.flatMap(_.templ.stats) else c.parent.toList.flatMap(_.children)
      val companion = siblings.collectFirst { case o: Defn.Object if o.name.value == className.value => o }
      val declarations = scala.collection.mutable.LinkedHashMap.empty[String,String]
      val occupied = companion.toList.flatMap(_.templ.stats).flatMap {
        case d: Defn.Type => List(d.name.value)
        case d: Defn.Object => List(d.name.value)
        case d: Defn.Class => List(d.name.value)
        case d: Defn.Trait => List(d.name.value)
        case d: Defn.Enum => List(d.name.value)
        case _ => Nil
      }.toSet
      ctor.paramClauses.filter(_.mod.isEmpty).flatMap(_.values).foreach { p =>
        val fieldKey = owner + "." + p.name.value
        val handled = scala.collection.mutable.Set.empty[Int]
        buffers.get(fieldKey).foreach { element =>
          require(Set("Byte","Int")(element),"Unsupported reviewed buffer element: " + fieldKey)
          val name = names.getOrElse(fieldKey,upper(p.name.value))
          p.decltpe.toList.flatMap(_.collect { case a: Type.Apply if Set("Array","scala.Array","_root_.scala.Array")(a.tpe.syntax) => a }).foreach { a =>
            require(a.argClause.values.size == 1 && !ancestry.exists(_.is[Defn.Def]),"Unsupported buffer owner or shape: " + fieldKey)
            val leaf = a.argClause.values.head
            val correction = base(leaf).isEmpty
            val baseType = "_root_.scala.Array[" + qualified(element) + "]"
            if(correction) {
              require(leaf.syntax == className.value + "." + name,"Unexpected prior buffer element: " + fieldKey)
              val obj = companion.toList.flatMap(_.templ.stats).collectFirst { case o: Defn.Object if o.name.value == name => o }.getOrElse(sys.error("Missing prior buffer type"))
              require(obj.templ.inits.size == 1 && obj.templ.stats.isEmpty)
              val parent = obj.templ.inits.head.tpe.asInstanceOf[Type.Apply]
              require(parent.tpe.syntax.endsWith("RefinedType") && parent.argClause.values.size == 2 &&
                parent.argClause.values.head.syntax == qualified(element) && parent.argClause.values(1).syntax.endsWith(".Pure"),"Prior buffer constraint/base differs: " + fieldKey)
              result += ((parent.argClause.values.head.pos.start,parent.argClause.values.head.pos.end,baseType))
            } else {
              require(base(leaf).contains(element) && !occupied(name),"Buffer base or companion collision: " + fieldKey)
              declarations(name) = baseType
            }
            handled += a.pos.start
            result += ((a.pos.start,a.pos.end,className.value + "." + name))
            evidence += List(path,(a.pos.startLine + 1).toString,(if(correction) "CORRECTION " else "") + owner + "#" + p.name.value + "/buffer " + a.structure,className.value + "." + name)
          }
        }
        p.decltpe.toList.flatMap(leaves(_)).filterNot { case(t,_,_) => parents(t).exists(x => handled(x.pos.start)) }.foreach { case(t,b,suffix) =>
          val key = owner + "." + p.name.value + (if(suffix.isEmpty) "" else "." + suffix)
          val defaultName = upper(p.name.value) + suffix
          val name = names.getOrElse(key,if(defaultName == className.value) "Value" else defaultName)
          val mappings = reuse.toList.collect { case(pattern,value) if
            key.matches(pattern.split("\\*",-1).map(java.util.regex.Pattern.quote).mkString(".*")) => value }.distinct
          require(mappings.size <= 1,"Conflicting concept mappings: " + key)
          val target = mappings.headOption.filterNot(t => t == owner + "." + name && !occupied(name))
          val shadowed = tree.collect { case n: Type.Name if n.value == b && n.pos.start != t.pos.start &&
            n.parent.exists {
              case d: Defn.Type => d.name eq n
              case d: Defn.Class => d.name eq n
              case _ => false
            } => n }.nonEmpty
          val reason = defer.get(owner + "." + p.name.value)
          val packed = parents(t).takeWhile(x => x.pos.start >= p.pos.start && x.pos.end <= p.pos.end).exists {
            case a: Type.Apply => Set("Array","scala.Array","_root_.scala.Array","js.Array")(a.tpe.syntax)
            case _ => false
          }
          val blocked = reason.nonEmpty || packed || enumCompanion.exists(_.templ.stats.isEmpty) || !name.matches("[A-Z][A-Za-z0-9_]*") || (ancestry.exists(_.is[Defn.Def]) && !localModels.contains(owner)) || shadowed ||
            (target.isEmpty && (occupied(name) || companion.exists(_.templ.stats.isEmpty) || declarations.get(name).exists(_ != qualified(b))))
          if(blocked) evidence += List(path,(t.pos.startLine + 1).toString,"UNRESOLVED " + key,t.structure + reason.map(" deferred: " + _).getOrElse(""))
          else {
            val replacement = target.getOrElse(referenceOwner + "." + name)
            if(target.isEmpty) declarations(name) = qualified(b)
            result += ((t.pos.start,t.pos.end,replacement))
            evidence += List(path,(t.pos.startLine + 1).toString,owner + "#" + p.name.value + "/" + suffix + " " + t.structure,replacement)
          }
        }
      }
      if(declarations.nonEmpty) {
        val pad = companion.map(o => indent(o.templ.stats.head)).getOrElse {
          if(enclosingEnum.nonEmpty) enumCompanion.map(o => indent(o.templ.stats.head) + "  ").getOrElse(indent(enclosingEnum.get) + "    ")
          else indent(c) + "  "
        }
        val definitions = declarations.toList.flatMap { case(n,b) => List(
          "type " + n + " = " + n + ".T",
          "object " + n + " extends _root_.io.github.iltotore.iron.RefinedType[" + b +
            ", _root_.io.github.iltotore.iron.constraint.any.Pure]") }.mkString("\n" + pad)
        companion match {
          case Some(o) => val start = o.templ.stats.head.pos.start; result += ((start,start,definitions + "\n\n" + pad))
          case None if enclosingEnum.nonEmpty =>
            enumCompanion match {
              case Some(o) =>
                val at = o.templ.stats.head.pos.start
                val outerPad = indent(o.templ.stats.head)
                result += ((at,at,"object " + className.value + ":\n" + pad + definitions + "\n\n" + outerPad))
              case None =>
                val e = enclosingEnum.get
                result += ((e.pos.end,e.pos.end,"\n\n" + indent(e) + "object " + e.name.value + ":\n" + indent(e) + "  object " + className.value + ":\n" + pad + definitions))
            }
          case None => result += ((c.pos.end,c.pos.end,"\n\n" + indent(c) + "object " + className.value + ":\n" + pad + definitions))
        }
      }
    }
    result.toList
  }
}
