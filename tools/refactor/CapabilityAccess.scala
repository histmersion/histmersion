import scalafix.v1._
import scala.meta._
import metaconfig.{ConfDecoder, Configured, generic}
import scalafix.lint.LintSeverity
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters._

object CapabilityAccess {
  final case class Example(path: String = "", line: Int = 0, snippet: String = "", rule: String = "", count: Int = 0, reason: String = "")
  object Example {
    implicit val surface: generic.Surface[Example] = generic.deriveSurface[Example]
    implicit val decoder: ConfDecoder[Example] = generic.deriveDecoder(Example()).noTypos
  }
  final case class Ban(id: String = "", symbols: List[String] = Nil, prefixes: List[String] = Nil,
      within: List[String] = Nil, outside: List[String] = Nil, repositoriesOnly: Boolean = false, message: String = "", allowedParameterTypes: List[String] = Nil,
      literalSymbols: List[String] = Nil, literalPattern: String = "")
  object Ban {
    implicit val surface: generic.Surface[Ban] = generic.deriveSurface[Ban]
    implicit val decoder: ConfDecoder[Ban] = generic.deriveDecoder(Ban()).noTypos
  }
  final case class Permission(id: String = "", owner: String = "", capabilities: List[String] = Nil, reason: String = "")
  object Permission {
    implicit val surface: generic.Surface[Permission] = generic.deriveSurface[Permission]
    implicit val decoder: ConfDecoder[Permission] = generic.deriveDecoder(Permission()).noTypos
  }
  final case class Policy(bans: List[Ban] = Nil, permissions: List[Permission] = Nil,
      repositoryOwners: List[String] = Nil, repositorySuffixes: List[String] = List("Repo"),
      report: String = "", verifyFixtures: Boolean = false, expectedFiles: List[String] = Nil,
      sourceRoots: List[String] = Nil, sourcePathContains: String = "/src/main/", bestEffort: Boolean = false,
      examples: List[Example] = Nil, measureOnly: Boolean = false)
  object Policy {
    implicit val surface: generic.Surface[Policy] = generic.deriveSurface[Policy]
    implicit val decoder: ConfDecoder[Policy] = generic.deriveDecoder(Policy()).noTypos
  }
  final case class Hit(id: String, path: String, line: Int, column: Int, owner: String, symbol: String,
      source: String, ast: String, message: String, position: Position, trace: List[String] = Nil)
  final case class Call(from: String, to: String, repository: Boolean, owners: List[String], site: Hit)
}

class CapabilityAccess(val policy: CapabilityAccess.Policy) extends SemanticRule("CapabilityAccess") {
  def this() = this(CapabilityAccess.Policy())
  import CapabilityAccess._
  private val records = new ConcurrentLinkedQueue[Hit]()
  private val files = new ConcurrentLinkedQueue[String]()
  private val permitted = new ConcurrentLinkedQueue[String]()
  private val fixtureFailures = new ConcurrentLinkedQueue[String]()
  private val calls = new ConcurrentLinkedQueue[Call]()
  private val seeds = new ConcurrentLinkedQueue[(String, String, String)]()
  private val fixtureExpectations = new ConcurrentLinkedQueue[(String, Map[Int, List[String]])]()
  private val unresolved = new ConcurrentLinkedQueue[Hit]()
  private val checkedExamples = new ConcurrentLinkedQueue[Example]()
  private val matched = policy.bans.map(b => b -> SymbolMatcher.normalized(b.symbols: _*))
  private val literalMatchers = policy.bans.filter(_.literalSymbols.nonEmpty).map(b => b -> SymbolMatcher.normalized(b.literalSymbols: _*)).toMap
  private val literalPatterns = policy.bans.filter(_.literalSymbols.nonEmpty).map(b => b -> b.literalPattern.r).toMap

  override def withConfiguration(config: Configuration): Configured[Rule] =
    config.conf.getOrElse("CapabilityAccess")(Policy()).andThen { p =>
      val ids = p.bans.map(_.id)
      val problems = List(
        Option.when(p.bans.isEmpty)("bans must be nonempty"),
        Option.when(ids.distinct.size != ids.size)("duplicate ban id"),
        Option.when(p.permissions.map(_.id).distinct.size != p.permissions.size)("duplicate permission id"),
        Option.when(p.bans.exists(b => b.id.isEmpty || b.message.isEmpty || (b.symbols.isEmpty && b.prefixes.isEmpty && b.literalSymbols.isEmpty)))("every ban requires id, message and symbols or prefixes"),
        Option.when(p.bans.exists(b => b.literalSymbols.nonEmpty != b.literalPattern.nonEmpty || scala.util.Try(b.literalPattern.r).isFailure))("literal matching requires both symbols and a valid pattern"),
        Option.when(p.permissions.exists(a => a.id.isEmpty || a.owner.isEmpty || a.reason.isEmpty || a.capabilities.isEmpty || !a.capabilities.forall(ids.contains)))("invalid permission: require id, owner, reason and known capabilities"),
        Option.when(p.examples.exists(e => e.path.isEmpty || e.line < 1 || e.snippet.isEmpty || !ids.contains(e.rule) || e.count < 0 || e.reason.isEmpty) ||
          p.examples.map(e => (e.path, e.line, e.rule)).distinct.size != p.examples.size)("invalid/duplicate source expectation")
      ).flatten
      if (problems.nonEmpty) Configured.error(problems.mkString("; ")) else Configured.ok(new CapabilityAccess(p))
    }

  private def parents(t: Tree): List[Tree] = t.parent.toList.flatMap(p => p :: parents(p))
  private def ownerNames(t: Tree): List[Name] = parents(t).collect {
    case d: Defn.Def => d.name
    case d: Defn.Class => d.name
    case d: Defn.Trait => d.name
    case d: Defn.Object => d.name
    case d: Defn.Enum => d.name
  }
  private def isDefinition(n: Name): Boolean = n.parent.exists {
    case m: Member => m.name eq n
    case p: Pat.Var => p.name eq n
    case _ => false
  }
  private def relative(input: Input): String = input match {
    case Input.File(p, _) => p.toString
    case Input.VirtualFile(p, _) => p
    case x => x.toString
  }
  private def isInvoked(t: Tree): Boolean = t.parent.exists {
    case s: Term.Select if s.name eq t => isInvoked(s)
    case a: Term.ApplyType if a.fun eq t => isInvoked(a)
    case a: Term.Apply if a.fun eq t => true
    case a: Term.ApplyInfix if a.op eq t => true
    case _ => false
  }
  private def graphKey(symbol: Symbol, path: String): String =
    if (symbol.isLocal) s"$path::${symbol.value}" else symbol.value

  private def matches(b: Ban, matcher: SymbolMatcher, s: Symbol): Boolean =
    !s.isNone && (matcher.matches(s) || b.prefixes.exists(s.value.startsWith))

  private def invocation(t: Tree): Option[Term.Apply] = t.parent.flatMap {
    case s: Term.Select if s.name eq t => invocation(s)
    case a: Term.ApplyType if a.fun eq t => invocation(a)
    case a: Term.Apply if a.fun eq t => Some(a)
    case _ => None
  }
  private def literalCall(b: Ban, n: Name)(implicit doc: SemanticDocument): Boolean =
    literalMatchers.get(b).exists(_.matches(n.symbol))
  private def literalMatch(b: Ban, n: Name)(implicit doc: SemanticDocument): Boolean =
    literalCall(b,n) && invocation(n).flatMap(_.argClause.values.headOption).exists {
      case Lit.String(value) => literalPatterns(b).findPrefixOf(value).nonEmpty
      case _ => false
    }
  private def matchesAt(b: Ban, matcher: SymbolMatcher, n: Name)(implicit doc: SemanticDocument): Boolean =
    matches(b,matcher,n.symbol) || literalMatch(b,n)
  private def contextAt(n: Name)(implicit doc: SemanticDocument): Tree =
    if(policy.bans.exists(b => literalMatch(b,n))) invocation(n).getOrElse(n.parent.getOrElse(n)) else n.parent.getOrElse(n)

  private def approvedOverload(b: Ban, s: Symbol)(implicit doc: SemanticDocument): Boolean =
    b.allowedParameterTypes.nonEmpty && s.info.exists(_.signature match {
      case MethodSignature(_, parameterLists, _) => parameterLists.flatten.exists(_.signature match {
        case ValueSignature(TypeRef(_, tpe, _)) => b.allowedParameterTypes.contains(tpe.value)
        case _ => false
      })
      case _ => false
    })

  private def redundantQualifier(n: Name, b: Ban, matcher: SymbolMatcher)(implicit doc: SemanticDocument): Boolean =
    {
      def outer(t: Tree): Boolean = t.parent.exists {
        case s: Term.Select if s.qual eq t => matches(b, matcher, s.name.symbol) || outer(s)
        case s: Term.Select if s.name eq t => outer(s)
        case s: Type.Select if s.name eq t => outer(s)
        case a: Term.Apply if a.fun eq t => outer(a)
        case a: Term.ApplyType if a.fun eq t => outer(a)
        case a: Type.Apply if a.tpe eq t => outer(a)
        case i: Init if i.tpe eq t => outer(i)
        case n: Term.New if n.init eq t => outer(n)
        case _ => false
      }
      outer(n)
    }

  override def fix(implicit doc: SemanticDocument): Patch = {
    val path = relative(doc.input)
    if (policy.sourceRoots.nonEmpty && !path.contains(policy.sourcePathContains)) return Patch.empty
    files.add(path)
    val lines = doc.input.text.linesIterator.toVector
    policy.examples.filter(e => Paths.get(e.path).toAbsolutePath.normalize == Paths.get(path).toAbsolutePath.normalize).foreach { e =>
      checkedExamples.add(e)
      if (!lines.lift(e.line - 1).exists(_.contains(e.snippet))) fixtureFailures.add(s"Stale capability example ${e.path}:${e.line}: ${e.snippet}")
    }
    val hits = doc.tree.collect {
      case n: Name if !isDefinition(n) && !parents(n).exists(_.is[Import]) => n
    }.flatMap { n =>
      val symbol = n.symbol
      val owners = ownerNames(n)
      val ownerSymbols = owners.map(_.symbol.value).filter(_.nonEmpty)
      val owner = ownerSymbols.headOption.getOrElse("")
      if (policy.bestEffort && symbol.isNone && owners.nonEmpty && (n.is[Term.Name] || n.is[Type.Name])) {
        val context = n.parent.getOrElse(n)
        unresolved.add(Hit("unresolved", path, n.pos.startLine + 1, n.pos.startColumn + 1, owner, "",
          context.pos.text, context.structure, "No resolved symbol; not evidence of compliance", n.pos))
      }
      val repo = ownerSymbols.exists(policy.repositoryOwners.contains) || owners.exists(o =>
        o.parent.exists(t => t.is[Defn.Class] || t.is[Defn.Object] || t.is[Defn.Trait]) && policy.repositorySuffixes.exists(o.value.endsWith))
      val method = owners.find(_.parent.exists(_.is[Defn.Def])).map(_.symbol).filterNot(_.isNone).map(graphKey(_, path))
      matched.foreach { case (ban, _) =>
        if(repo && literalCall(ban,n)) invocation(n).flatMap(_.argClause.values.headOption).foreach { argument =>
          val unsupported = argument match {
            case Lit.String(value) => value.trim.startsWith("--") || value.trim.startsWith("/*") || value.dropRight(1).contains(';')
            case _ => true
          }
          if(unsupported) unresolved.add(Hit("unresolved-transaction-sql",path,n.pos.startLine + 1,n.pos.startColumn + 1,owner,symbol.value,
            argument.pos.text,argument.structure,"SQL expression needs effect review; not evidence of compliance",n.pos))
        }
      }
      if (!symbol.isNone && isInvoked(n)) method.foreach { caller =>
        val context = contextAt(n)
        calls.add(Call(caller, graphKey(symbol, path), repo, ownerSymbols,
          Hit("", path, n.pos.startLine + 1, n.pos.startColumn + 1, owner, symbol.value,
            context.pos.text, context.structure, "", n.pos)))
        matched.foreach { case (ban, matcher) =>
          if (ban.repositoriesOnly && matchesAt(ban, matcher, n) && !approvedOverload(ban, symbol))
            seeds.add((caller, ban.id, graphKey(symbol, path)))
        }
      }
      matched.collect {
        case (ban, matcher) if matchesAt(ban, matcher, n) && !redundantQualifier(n, ban, matcher) && !approvedOverload(ban, symbol) &&
          (ban.within.isEmpty || ownerSymbols.exists(o => ban.within.exists(o.startsWith))) &&
          !ownerSymbols.exists(o => ban.outside.exists(o.startsWith)) &&
          (!ban.repositoriesOnly || repo) =>
          val allowance = policy.permissions.find(a => a.capabilities.contains(ban.id) && ownerSymbols.contains(a.owner))
          allowance match {
            case Some(a) => permitted.add(a.id); None
            case None =>
              val context = contextAt(n)
              Some(Hit(ban.id, path, n.pos.startLine + 1, n.pos.startColumn + 1, owner, symbol.value,
                context.pos.text, context.structure, ban.message, n.pos))
          }
      }.flatten
    }.distinctBy(h => (h.id, h.line, h.column, h.symbol))
    records.addAll(hits.asJava)
    if (policy.verifyFixtures) {
      val expected = doc.tokens.collect { case c: Token.Comment if c.text.startsWith("// expect: ") =>
        c.pos.startLine + 1 -> c.text.stripPrefix("// expect: ").trim.split(",").toList.sorted
      }.toMap
      fixtureExpectations.add(path -> expected)
      Patch.empty
    } else if (policy.measureOnly) Patch.empty
    else hits.map(h => Patch.lint(Diagnostic(h.id, s"${h.message}\nowner: ${h.owner}\nsymbol: ${h.symbol}", h.position, severity = LintSeverity.Error))).asPatch
  }

  override def afterComplete(): Unit = {
    val edges = calls.asScala.toList.distinct.sortBy(e => (e.site.path, e.site.line, e.site.column))
    val reaches = scala.collection.mutable.Map.empty[(String, String), List[String]]
    seeds.asScala.toList.sorted.foreach { case (owner, ban, target) => reaches.getOrElseUpdate((owner, ban), List(target)) }
    val repositoryBans = policy.bans.filter(_.repositoriesOnly)
    var changed = true
    while (changed) {
      changed = false
      edges.foreach { edge => repositoryBans.foreach { ban =>
        reaches.get((edge.to, ban.id)).foreach { path =>
          if (!reaches.contains((edge.from, ban.id))) {
            reaches((edge.from, ban.id)) = edge.to :: path
            changed = true
          }
        }
      }}
    }
    val directLocations = records.asScala.map(h => (h.id, h.path, h.line, h.column)).toSet
    val indirect = edges.filter(_.repository).flatMap { edge => repositoryBans.flatMap { ban =>
      val allowance = policy.permissions.find(p => p.capabilities.contains(ban.id) && edge.owners.contains(p.owner))
      val inScope = (ban.within.isEmpty || edge.owners.exists(o => ban.within.exists(o.startsWith))) &&
        !edge.owners.exists(o => ban.outside.exists(o.startsWith))
      reaches.get((edge.to, ban.id)).filter(_ => inScope &&
        !directLocations((ban.id, edge.site.path, edge.site.line, edge.site.column))).flatMap { path =>
        allowance match {
          case Some(p) => permitted.add(p.id); None
          case None => Some(edge.site.copy(id = ban.id, message = ban.message, trace = edge.to :: path))
        }
      }
    }}.distinctBy(h => (h.id, h.path, h.line, h.column))
    records.addAll(indirect.asJava)
    checkedExamples.asScala.foreach { e =>
      val actual = records.asScala.count(h => Paths.get(h.path).toAbsolutePath.normalize == Paths.get(e.path).toAbsolutePath.normalize && h.line == e.line && h.id == e.rule)
      if (actual != e.count) fixtureFailures.add(s"${e.path}:${e.line} ${e.rule}: expected ${e.count}, got $actual")
    }
    val uncheckedExamples = policy.examples.filterNot(checkedExamples.asScala.toSet)
    fixtureExpectations.asScala.foreach { case (path, expected) =>
      val actual = records.asScala.filter(_.path == path).toList.groupBy(_.line).view.mapValues(_.map(_.id).sorted).toMap
      if (actual != expected) fixtureFailures.add(s"$path\nexpected=$expected\nactual=$actual")
    }
    val expectedSources = policy.sourceRoots.flatMap { root =>
      val stream = Files.walk(Paths.get(root).toAbsolutePath.normalize)
      try stream.iterator.asScala.filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala") &&
        p.toString.contains(policy.sourcePathContains)).map(_.toString).toList
      finally stream.close()
    }.distinct.sorted
    val actualSources = files.asScala.map(p => Paths.get(p).toAbsolutePath.normalize.toString).toSet
    val missing = expectedSources.filterNot(actualSources)
    if (policy.report.nonEmpty) {
      val out = Paths.get(policy.report)
      Files.createDirectories(out)
      Files.write(out.resolve("rules.txt"), policy.bans.map(_.id).asJava, UTF_8)
      def escaped(s: String): String = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")
      val hits = records.asScala.toList.sortBy(h => (h.path, h.line, h.column, h.id))
      val rows = "id\tpath\tline\tcolumn\towner\tsymbol\tsource\tast\tcall-chain" :: hits.map(h =>
        List(h.id, h.path, h.line.toString, h.column.toString, h.owner, h.symbol, h.source, h.ast, h.trace.mkString(" -> ")).map(escaped).mkString("\t"))
      Files.write(out.resolve("findings.tsv"), rows.asJava, UTF_8)
      Files.write(out.resolve("verification.txt"), (List(s"examples=${checkedExamples.size}/${policy.examples.size}",
        s"failures=${fixtureFailures.size}", s"unchecked-examples=${uncheckedExamples.size}") ++ fixtureFailures.asScala).asJava, UTF_8)
      Files.write(out.resolve("unresolved.tsv"), ("path\tline\tcolumn\towner\tsource\tast" :: unresolved.asScala.toList
        .sortBy(h => (h.path, h.line, h.column)).map(h => List(h.path, h.line.toString, h.column.toString, h.owner,
          h.source, h.ast).map(escaped).mkString("\t"))).asJava, UTF_8)
      Files.write(out.resolve("files.txt"), files.asScala.toList.sorted.asJava, UTF_8)
      Files.write(out.resolve("missing-files.txt"), missing.asJava, UTF_8)
      def moduleOf(path: String): String = path.split("/modules/").last.split("/src/").head
      val modules = expectedSources.map(moduleOf).distinct.sorted
      Files.write(out.resolve("coverage.tsv"), ("module\texpected\tanalyzed\tmissing" :: modules.map { module =>
        val expected = expectedSources.count(p => moduleOf(p) == module)
        val absent = missing.count(p => moduleOf(p) == module)
        s"$module\t$expected\t${expected - absent}\t$absent"
      }).asJava, UTF_8)
      Files.write(out.resolve("summary.txt"), (List(
        s"status=${if (missing.nonEmpty || policy.bestEffort) "INCOMPLETE" else if (hits.nonEmpty) "VIOLATIONS" else "NO_DIRECT_VIOLATIONS"}",
        s"best-effort=${policy.bestEffort}", s"unresolved-reference-names=${unresolved.size}",
        s"expected-files=${expectedSources.size}", s"missing-files=${missing.size}", s"files=${files.size}", s"violations=${hits.size}",
        s"indirect-repository-findings=${indirect.size}",
        "coverage=direct resolved references plus repository calls through known source method bodies; dynamic dispatch, callbacks, external helper bodies and inferred-type escapes remain unmeasured",
        "unresolved-reference-names includes unsupported transaction SQL expressions; missing-symbol names are collected only in best-effort mode",
        "SQL controls: configured JDBC method symbols with a direct string literal and a recognized leading command; dynamic, comment-prefixed and compound SQL require review",
        "completion=check Scalafix exit status for missing/stale SemanticDB and parse failures; this file alone is not a pass") ++
        policy.bans.map(b => s"${b.id}=${hits.count(_.id == b.id)}") ++
        policy.permissions.map(p => s"permission:${p.id}:uses=${permitted.asScala.count(_ == p.id)}")).asJava, UTF_8)
    }
    if (policy.expectedFiles.nonEmpty) {
      val actualFiles = files.asScala.map(p => Paths.get(p).getFileName.toString).toList.sorted
      require(actualFiles == policy.expectedFiles.sorted, s"Incomplete fixture coverage: expected ${policy.expectedFiles.sorted}, got $actualFiles")
    }
    require(fixtureFailures.isEmpty, fixtureFailures.asScala.mkString("\n"))
    require(uncheckedExamples.isEmpty, s"Unchecked capability examples: $uncheckedExamples")
    if (policy.verifyFixtures) require(files.size > 0, "No fixtures analyzed")
    require(missing.isEmpty, s"Incomplete semantic coverage: ${missing.size} expected sources were not analyzed; see missing-files.txt")
    if (!policy.verifyFixtures) require(!policy.bestEffort, "Best-effort compiler evidence is provisional and cannot pass migration verification; inspect compiler errors and unresolved.tsv")
    if (!policy.verifyFixtures && !policy.measureOnly) require(indirect.isEmpty, s"${indirect.size} indirect repository capability violations; see findings.tsv call-chain column")
  }
}
