package com.histmersion.linting

import scala.annotation.tailrec
import scalafix.v1.*

final class ContractLint extends SyntacticRule("ContractLint"):
  override def fix(using doc: SyntacticDocument): Patch =
    Tolerance.patch(Tolerance.diagnostics(Contracts.findings(doc), doc.tree, Contracts.Owned))

final case class Signature(name: String, at: Int, params: List[(String, String)], result: String)

object Contracts:

  val Names: Map[String, String] = Map(
    "primitive" -> "Primitive",
    "layer" -> "Layer",
    "client-refined" -> "ClientRefined",
    "concept" -> "Concept",
    "derives" -> "Derives",
    "copy" -> "Copy",
    "one-type" -> "OneType",
    "transaction" -> "Transaction",
    "global" -> "Global",
    "service" -> "Service",
    "helper" -> "Helper",
    "prefix" -> "Prefix",
    "bucket" -> "Bucket",
    "circe" -> "Circe",
    "scallop" -> "Scallop",
    "nonempty-pattern" -> "NonEmptyPattern",
    "problem-field" -> "ProblemField",
    "unrecognised" -> "Unrecognised",
    "relayed" -> "Relayed",
    "first-problem" -> "FirstProblem",
    "html-sink" -> "HtmlSink",
    "html-newtype" -> "HtmlNewtype",
    "failure-request" -> "FailureRequest",
    "raw-request" -> "RawRequest",
    "schema-config" -> "SchemaConfig",
    "unclassified" -> "Unclassified",
    "default" -> "CaseDefault",
    "canequal" -> "CanEqualHome"
  )

  val Owned: Set[String] = Names.values.toSet

  def findings(doc: SyntacticDocument): List[Finding] = Lints.textFindings(doc.input, violations, Names)

  val Classified: List[String] =
    List("values", "config", "files", "plugin-api", "plugin-resolver", "upstreams", "plugin-vaults") ++
      List("plugins/events", "plugins/fetchers", "plugins/gazetteers") ++
      List("plugin-host", "plugin-testkit", "plugins/media", "plugins/calibre", "plugins/ai-http") ++
      List(
        "plugin-fixtures/hello",
        "plugin-fixtures/hello-ai",
        "plugin-fixtures/hello-deps",
        "plugin-fixtures/hello-lib"
      ) ++
      List("api")

  val Modules: List[String] = List(
    "problem",
    "json",
    "values",
    "api",
    "config",
    "files",
    "core",
    "db",
    "upstreams",
    "plugin-api",
    "plugin-host",
    "plugin-resolver",
    "plugin-vaults",
    "chimney-iron",
    "server",
    "cli",
    "web",
    "plugins/ai-http",
    "plugins/calibre",
    "plugins/events",
    "plugins/fetchers",
    "plugins/gazetteers",
    "plugins/media"
  )

  def layerOf(module: String): String = module match
    case "api" | "web"                 => "api"
    case "db"                          => "db"
    case "cli"                         => "cli"
    case "plugin-api"                  => "plugin"
    case m if m.startsWith("plugins/") => m
    case "values" | "core" | "config" | "files" | "upstreams" | "server" | "chimney-iron" | "plugin-host" |
        "plugin-resolver" | "plugin-vaults" =>
      "core"
    case m => m

  lazy val violations: Map[String, List[Violation]] =
    val main = Modules.flatMap(SourceFile.all(_))
    val classified = Classified.flatMap(m => SourceFile.all(m) ++ SourceFile.all(m, "test"))
    val unclassified = Rules.unclassified(classified)
    val defaults = Rules.caseDefaults(SourceFile.all("problem"))
    (Rules.all(main) ++ unclassified ++ defaults ++ Rules.equalityHomes(main)).groupBy(_.file)

  object Rules:
    def all(sources: List[SourceFile]): List[Violation] =
      val concepts = newtypes(sources)
      val configured = sources.filter(s => TheConfig.findFirstIn(s.code).isDefined).map(_.pkg).toSet
      sources.flatMap(s =>
        primitive(s, concepts) ++ derives(s) ++ copies(s) ++ oneType(s) ++ transaction(s) ++
          global(s) ++ service(s) ++ layer(s) ++ clientRefined(s) ++ bucket(s) ++
          circe(s) ++
          (if scallopAdapter(s.key) then Nil
           else
             counted(
               "scallop",
               s,
               "scallop outside the command-line adapters",
               Scallop.findAllMatchIn(s.code).map(_.start).toList
             )) ++
          counted(
            "nonempty-pattern",
            s,
            "a type pattern on NonEmptyVector outside the problem module",
            if module(s) == "problem" then Nil else NonEmptyPattern.findAllMatchIn(s.code).map(_.start).toList
          )
          ++ counted(
            "unrecognised",
            s,
            "Stored.Unrecognised built outside the stored column's reader",
            if Unrecognisers(s.key) then Nil
            else Unrecognised.findAllMatchIn(s.code).filterNot(m => inPattern(s.code, m.start)).map(_.start).toList
          )
          ++ counted(
            "relayed",
            s,
            "Problem.Relayed built outside the browser's decoder",
            if Relayers(s.key) then Nil
            else Relayed.findAllMatchIn(s.code).filterNot(m => inPattern(s.code, m.start)).map(_.start).toList
          )
          ++ counted(
            "first-problem",
            s,
            "the first of several problems kept, the rest dropped",
            if FirstProblemPickers(s.key) then Nil else FirstProblem.findAllMatchIn(s.code).map(_.start).toList
          )
          ++ counted(
            "html-sink",
            s,
            "a DOM markup sink written to outside the one that takes Html",
            if s.key == HtmlSinkFile then Nil else HtmlSinkWrite.findAllMatchIn(s.code).map(_.start).toList
          )
          ++ counted(
            "html-newtype",
            s,
            "an …Html newtype over String: markup any text builds, beside api.Html",
            HtmlNewtype.findAllMatchIn(s.code).map(_.start).toList
          )
          ++ counted(
            "failure-request",
            s,
            "a request sent from a failure branch: one request's failure decides another",
            if module(s) == "web" then failureRequests(s.code) else Nil
          )
          ++ counted(
            "raw-request",
            s,
            "requests consumed outside the one request type: no newest-wins guard, failures dropped or flattened",
            if module(s) == "web" && !RequestFiles(s.key) then rawRequests(s.code) else Nil
          )
          ++ counted(
            "schema-config",
            s,
            "a schema derived where the codecs' JsoniterConfig is not in scope, or a JsoniterConfig of its own (ADR-0026)",
            schemaConfigMisses(s.code, (s.pkg :: packagesInScope(s.code)).exists(configured))
          )
      ) ++ concept(sources) ++ helper(sources) ++ prefix(sources) ++ problemFields(sources)

    private def module(s: SourceFile): String = s.key.takeWhile(_ != ':')
    private def baseName(s: SourceFile): String = s.key.split('/').last.stripSuffix(".scala")

    private def counted(rule: String, s: SourceFile, detail: String, hits: List[Int]): List[Violation] =
      hits.headOption.toList.map(h => Violation(rule, s.key, detail, hits.size, s"${s.key}:${s.lineOf(h)}", hits))

    private def one(rule: String, s: SourceFile, detail: String, at: Int): Violation =
      Violation(rule, s.key, detail, 1, s"${s.key}:${s.lineOf(at)}", List(at))

    private val Newtype =
      "\\bobject\\s+([A-Z]\\w*)\\s+extends\\s+Refined(?:Sub)?Type\\b|\\bopaque\\s+type\\s+([A-Z]\\w*)".r

    def newtypes(sources: List[SourceFile]): Set[String] =
      sources
        .flatMap(s =>
          Newtype.findAllMatchIn(s.code).map { m =>
            val name = Option(m.group(1)).getOrElse(m.group(2))
            val line = s.code.substring(s.code.lastIndexOf('\n', m.start) + 1, m.start)
            if line.isBlank && line.nonEmpty then
              Enclosing
                .findAllMatchIn(s.code.substring(0, m.start))
                .toList
                .lastOption
                .fold(name)(o => o.group(1) + name)
            else name
          }
        )
        .toSet - "X" - "T"

    private val Enclosing = "(?m)^(?:(?:final|private|case)(?:\\[\\w+\\])?\\s+)*object\\s+([A-Z]\\w*)".r

    def conceptOf(name: String, concepts: Set[String]): Option[String] =
      val forms = List(name, name.stripSuffix("s")).distinct
      concepts.toList
        .filter(c => forms.exists(f => f.equalsIgnoreCase(c) || endsWithWord(f, c)))
        .sortBy(-_.length)
        .headOption

    private def endsWithWord(name: String, concept: String): Boolean =
      val at = name.length - concept.length
      at > 0 && name.substring(at).equalsIgnoreCase(concept) && name(at).isUpper

    private val Def = "\\bdef\\s+(\\w+)".r
    private val Member = "^  (?:(?:final|override|inline|transparent)\\s+)*$".r
    private val Param = "(\\w+)\\s*:\\s*(?:=>\\s*)?([^,()]*(?:\\([^()]*\\)[^,()]*)?)".r

    def signatures(code: String): List[Signature] =
      Def
        .findAllMatchIn(code)
        .filter(m => Member.matches(code.substring(code.lastIndexOf('\n', m.start) + 1, m.start)))
        .map { m =>
          val n = code.length
          var i = m.end
          var depth = 0
          val params = List.newBuilder[(String, String)]
          val chunk = new StringBuilder
          var stop = false
          while i < n && !stop do
            val c = code(i)
            if c == '(' || c == '[' then
              if depth == 0 && c == '(' then chunk.clear() else chunk += c
              depth += 1
            else if c == ')' || c == ']' then
              depth -= 1
              if depth == 0 && c == ')' then
                Param.findAllMatchIn(chunk.toString).foreach(p => params += (p.group(1) -> p.group(2).trim))
              else chunk += c
            else if depth == 0 && c == '\n' && continues(code, i) then chunk += c
            else if depth == 0 && (c == '=' && !code.startsWith("=>", i) || c == '\n' || c == '{') then stop = true
            else chunk += c
            i += 1
          val tail = code.substring(m.end, i - 1).replaceAll("\\)\\s*:", "):")
          val result = tail.lastIndexOf("):") match
            case -1 if !tail.trim.startsWith("(") && tail.contains(":") => tail.substring(tail.indexOf(':') + 1).trim
            case -1                                                     => ""
            case k                                                      => tail.substring(k + 2).trim
          Signature(m.group(1), m.start, params.result(), result)
        }
        .toList

    private def continues(code: String, newline: Int): Boolean =
      val next = code.indexWhere(c => !c.isWhitespace, newline)
      next >= 0 && (code(next) == '(' || code(next) == ':' && !code.startsWith("::", next))

    private val Primitive =
      "^(?:(?:Option|List|Set|Seq|Vector|Iterable|NonEmptyList|Chunk)\\[)?(?:String|Int|Long)\\]?$".r

    private def isContractFile(s: SourceFile): Boolean =
      val b = baseName(s)
      b.endsWith("Service") || b.endsWith("Repo") || b.endsWith("Controllers") || b.endsWith("Controller")

    private def primitive(s: SourceFile, concepts: Set[String]): List[Violation] =
      if !isContractFile(s) then Nil
      else
        val hits = signatures(s.code).flatMap { sig =>
          val ps = sig.params.collect {
            case (name, tpe) if Primitive.matches(tpe) && conceptOf(name, concepts).isDefined => sig.at
          }
          val r = if Primitive.matches(sig.result) && conceptOf(sig.name, concepts).isDefined then List(sig.at) else Nil
          ps ++ r
        }
        counted("primitive", s, "String/Int/Long for a concept with a newtype", hits)

    private val TopLevel =
      "(?m)^(?:(?:final|sealed|abstract|private|protected|open|case|implicit|transparent|inline|opaque)(?:\\[\\w+\\])?\\s+)*(class|trait|object|enum|type)\\s+([A-Z]\\w*)".r

    private def topLevel(s: SourceFile): List[(String, String, Int)] =
      TopLevel.findAllMatchIn(s.code).map(m => (m.group(1), m.group(2), m.start)).toList

    private def concept(sources: List[SourceFile]): List[Violation] =
      sources
        .flatMap(s => topLevel(s).map((_, name, at) => (layerOf(module(s)), name, s, at)))
        .groupBy(d => (d._1, d._2))
        .toList
        .flatMap {
          case ((layer, name), defs) if defs.map(_._3.platformless).distinct.size > 1 =>
            val files = defs.map(_._3.key).distinct.sorted
            defs.map((_, _, s, at) =>
              Violation(
                "concept",
                s.key,
                s"$name in ${files.mkString(" ")} (layer $layer)",
                1,
                s"${s.key}:${s.lineOf(at)}",
                List(at)
              )
            )
          case _ => Nil
        }

    private val Codecs =
      "(?:JsonValueCodec|JsonKeyCodec|JsonCodec|Encoder|Decoder|Codec|Schema|KeyEncoder|KeyDecoder|DbCodec|ConfigReader|Kindlings\\w*)"

    private val CirceAdapters = Set(
      "server:util/mcp/ChimpJson.scala",
      "server:domain/automation/agents/SttpAiJson.scala"
    )
    private val Circe =
      "\\bio\\.circe\\b|\\bsttp\\.tapir\\.json\\.circe\\b|\\bsttp\\.apispec\\.circe\\b|\\bcircederivation\\b".r

    def scallopAdapter(key: String): Boolean =
      key == "cli:cli/CommandLine.scala" || key.startsWith("cli:cli/commandline/")
    private val Scallop =
      "\\bScallopConf(?:Base)?\\b|\\bSubcommand\\b|(?<!def\\s)\\bopt\\[|(?<!def\\s)\\btrailArg\\[|\\baddSubcommand\\(|\\borg\\.rogach\\b".r

    private def circe(s: SourceFile): List[Violation] =
      if CirceAdapters(s.key) then Nil
      else counted("circe", s, "circe outside a listed adapter", Circe.findAllMatchIn(s.code).map(_.start).toList)
    private val HandGiven = ("(?m)^\\s*given\\s+(?:\\w+\\s*:\\s*)?" + Codecs + "\\[").r
    private val GivenStart =
      "(?m)^\\s*(?:(?:inline|transparent|private|protected|override)(?:\\[\\w+\\])?\\s+)*given\\b".r
    private val Generic = "^given\\s+(?:\\w+\\s*)?(?::\\s*)?[\\[(]".r
    private val CodecResult = ("^\\s*" + Codecs + "\\[").r

    def genericCodecGivens(code: String): List[Int] =
      GivenStart
        .findAllMatchIn(code)
        .flatMap { m =>
          val h = header(code, m.end - "given".length)
          Option.when(
            Generic.findFirstIn(h).isDefined && CodecResult.findFirstIn(resultType(h)).isDefined &&
              NewtypeLift.findFirstIn(h).isEmpty
          )(m.start)
        }
        .toList

    private val NewtypeLift = "\\bRefinedType\\.Mirror\\[|\\bTwoStates\\[|\\bTwoCases\\[".r

    private def resultType(h: String): String =
      var depth = 0
      var last = 0
      var i = 0
      while i < h.length do
        val c = h(i)
        if c == '(' || c == '[' then depth += 1
        else if c == ')' || c == ']' then depth -= 1
        else if depth == 0 && h.startsWith("=>", i) then
          last = i + 2
          i += 1
        else if depth == 0 && c == ':' then last = i + 1
        i += 1
      h.substring(last)

    val Derived = "(?<!\\bCanEqual)\\.derived\\b".r

    val CodecHomes: Set[String] = Set("api:api/json/TwoStates.scala")

    private def derives(s: SourceFile): List[Violation] =
      if module(s) == "json" || CodecHomes(s.key) then Nil
      else
        val hits =
          HandGiven.findAllMatchIn(s.code).map(_.start) ++ genericCodecGivens(s.code) ++
            Derived.findAllMatchIn(s.code).map(_.start)
        counted("derives", s, "codec given written by hand", hits.toList.distinct.sorted)

    private val EqualityGiven =
      "(?m)^[ \\t]*(?:(?:private|protected|inline)(?:\\[\\w+\\])?\\s+)*given\\s+(?:\\w+\\s*:\\s*)?CanEqual\\[".r

    private def simpleType(t: String): String = t.replaceAll("\\s", "").replaceFirst("^(?:[a-z]\\w*\\.)+", "")

    private def typeArgs(code: String, from: Int): List[String] =
      @tailrec def loop(i: Int, depth: Int, start: Int, acc: List[String]): List[String] =
        if i >= code.length then Nil
        else
          code(i) match
            case '[' | '('         => loop(i + 1, depth + 1, start, acc)
            case ']' if depth == 0 => (code.substring(start, i) :: acc).reverse
            case ']' | ')'         => loop(i + 1, depth - 1, start, acc)
            case ',' if depth == 0 => loop(i + 1, depth, i + 1, code.substring(start, i) :: acc)
            case _                 => loop(i + 1, depth, start, acc)
      loop(from, 0, from, Nil)

    def equalityGivens(code: String): List[(String, Int)] =
      EqualityGiven
        .findAllMatchIn(code)
        .flatMap(m =>
          typeArgs(code, m.end).map(simpleType) match
            case List(a, b) if a == b => Some(a -> m.start)
            case _                    => None
        )
        .toList

    def equalityTwice(sources: List[SourceFile]): Map[String, List[String]] =
      sources
        .flatMap(s => equalityGivens(s.code).map((t, _) => t -> s))
        .groupMap(_._1)(_._2)
        .collect {
          case (t, files) if files.map(_.platformless).distinct.size > 1 => t -> files.map(_.key).distinct.sorted
        }

    def equalityHomes(sources: List[SourceFile]): List[Violation] =
      val twice = equalityTwice(sources)
      sources.flatMap(s =>
        equalityGivens(s.code).collect {
          case (t, at) if twice.contains(t) =>
            one("canequal", s, s"CanEqual[$t, $t] given in ${twice(t).mkString(" ")}", at)
        }
      )

    private def copies(s: SourceFile): List[Violation] =
      counted("copy", s, ".copy(", "\\.copy\\(".r.findAllMatchIn(s.code).map(_.start).toList)

    private def header(code: String, at: Int): String =
      val n = code.length
      def restBlank(from: Int): Boolean =
        val k = code.indexWhere(c => c == '\n' || !c.isWhitespace, from)
        k < 0 || code(k) == '\n'
      def ends(i: Int): Boolean = code(i) match
        case '{'  => true
        case '='  => !code.startsWith("=>", i) && !code.startsWith("==", i)
        case ':'  => restBlank(i + 1)
        case '\n' => i + 1 >= n || !code(i + 1).isWhitespace || restBlank(i + 1)
        case _    => false
      var i = at
      var depth = 0
      while i < n && !(depth == 0 && ends(i)) do
        if code(i) == '(' || code(i) == '[' then depth += 1
        else if code(i) == ')' || code(i) == ']' then depth -= 1
        i += 1
      code.substring(at, i)

    private def oneType(s: SourceFile): List[Violation] =
      val defs = topLevel(s)
      val sealedNames = defs.collect { case (k, n, at) if s.code.startsWith("sealed", at) => n }.toSet
      val enums = defs.collect { case ("enum", n, _) => n }.toSet
      val parents = sealedNames ++ enums
      val cases = defs.collect {
        case (_, n, at)
            if parents.exists(p => ("\\bextends\\s+" + p + "\\b").r.findFirstIn(header(s.code, at)).isDefined) &&
              !parents(n) =>
          n
      }.toSet
      val all = defs.map(_._2).distinct
      val constraints = all.filter(n => n.endsWith("Rule") && all.contains(n.stripSuffix("Rule")))
      val names = all.filterNot(cases).filterNot(constraints.contains)
      if names.size <= 1 then Nil
      else
        List(
          Violation("one-type", s.key, names.mkString(" "), names.size, s"${s.key}:${s.lineOf(defs(1)._3)}", List(defs(1)._3))
        )

    private def transaction(s: SourceFile): List[Violation] =
      if !baseName(s).endsWith("Repo") then Nil
      else
        counted(
          "transaction",
          s,
          "opens a connection or transaction",
          "(?<![\\w.])(?:transact|connect)\\(|\\bTransactor\\b|\\.getConnection\\b".r
            .findAllMatchIn(s.code)
            .map(_.start)
            .toList
        ) ++ counted(
          "transaction",
          s,
          "takes a DataSource",
          signatures(s.code).filter(_.params.exists(_._2.contains("DataSource"))).map(_.at)
        )

    private val Globals: List[(String, scala.util.matching.Regex)] = List(
      "environment" -> "\\bSystem\\.getenv\\b|\\bsys\\.env\\b".r,
      "system property" -> "\\bSystem\\.getProperty\\b|\\bsys\\.props\\b|\\bSystem\\.getProperties\\b".r,
      "wall clock" -> "\\bSystem\\.currentTimeMillis\\b|\\b(?:Instant|LocalDate|LocalDateTime|OffsetDateTime|ZonedDateTime)\\.now\\(\\s*\\)|\\bClock\\.system\\w*".r
    )
    private val RawGlobals = "\"user\\.(?:home|dir)\"".r

    private def global(s: SourceFile): List[Violation] =
      val entry = baseName(s) == "SystemContext" || "@main\\s+def".r.findFirstIn(s.code).isDefined
      if entry then Nil
      else
        Globals.flatMap((kind, re) => counted("global", s, kind, re.findAllMatchIn(s.code).map(_.start).toList)) ++
          counted("global", s, "home or working directory", RawGlobals.findAllMatchIn(s.raw).map(_.start).toList)

    private val ServiceTrait = "(?m)^(?:sealed\\s+)?trait\\s+(\\w+Service)\\b".r

    private def service(s: SourceFile): List[Violation] =
      ServiceTrait.findAllMatchIn(s.code).toList.flatMap { m =>
        val missing =
          List("Implementation", "InMemory").filterNot(c => s"\\bclass\\s+$c\\b".r.findFirstIn(s.code).isDefined)
        if missing.isEmpty then Nil
        else List(one("service", s, s"${m.group(1)} without ${missing.mkString(" and ")}", m.start))
      }

    private val HelperSuffix = "^([A-Z]\\w*?)(s|Ops|Utils|Util|Helpers|Helper|Support|Syntax|Functions)$".r

    private def helper(sources: List[SourceFile]): List[Violation] =
      val types = sources.flatMap(s => topLevel(s).collect { case (k, n, _) if k != "object" => n }).toSet
      sources.flatMap { s =>
        val defs = topLevel(s)
        val own = defs.collect { case (k, n, _) if k != "object" => n }.toSet
        defs.collect {
          case ("object", name @ HelperSuffix(stem, _), at) if !own(name) && types(stem) && !types(name) =>
            one("helper", s, s"$name about $stem", at)
        }
      }

    private val ClientModules = Set("api", "web")
    private val DomainModules = Set("core", "db", "server", "files", "config", "plugin-host")
    private val Boundary = "(?:Routes|Endpoints|Endpoint|Tools|Transformers|Transformer|Controllers|Controller)$".r
    private val Named = "\\bcom\\.histmersion\\.(\\w+)".r
    private val SharedProblem = "com.histmersion.util.problem."
    private val SharedJson = "com.histmersion.util.json."
    private val SharedNonEmpty = "com.histmersion.util.nonempty."
    private val ProblemRenderers = "com.histmersion.api.problem."

    private def layer(s: SourceFile): List[Violation] =
      val m = module(s)
      if ClientModules(m) then
        val outside = Named
          .findAllMatchIn(s.code)
          .filterNot(n =>
            n.group(1) == "api" || n.group(1) == "web" || n.group(1) == "lint" || s.code.startsWith(SharedProblem, n.start) ||
              s.code.startsWith(SharedJson, n.start) || s.code.startsWith(SharedNonEmpty, n.start)
          )
        val own = if m == "api" && !s.pkg.startsWith("com.histmersion.api") then List(0) else Nil
        counted("layer", s, "the client names a package outside api", outside.map(_.start).toList.take(1)) ++
          counted("layer", s, "an api file outside com.histmersion.api", own)
      else if DomainModules(m) && s.pkg.startsWith("com.histmersion.domain") &&
        Boundary.findFirstIn(baseName(s)).isEmpty
      then
        counted(
          "layer",
          s,
          "a domain file names the api outside a boundary",
          "\\bcom\\.histmersion\\.api\\b".r
            .findAllMatchIn(s.code)
            .map(_.start)
            .filterNot(s.code.startsWith(ProblemRenderers, _))
            .toList
            .take(1)
        )
      else Nil

    private val NonEmptyPattern = "\\bcase\\b[^\\n]*?:\\s*NonEmptyVector\\[[^\\n]*?=>".r

    private val RefinedModules = Set("api", "web", "cli", "plugin-api")

    val ClientAuthorities: Set[String] =
      Set("cli:cli/PluginId.scala", "cli:cli/PluginRepository.scala", "cli:cli/ScalaPackage.scala")
    private val Refinement = "\\bRefined(?:Sub)?Type\\s*\\[[^,]+,\\s*(\\w+)|:\\|\\s*(\\w+)".r

    private def clientRefined(s: SourceFile): List[Violation] =
      if !RefinedModules(module(s)) || ClientAuthorities(s.key) then Nil
      else
        val hits = Refinement
          .findAllMatchIn(s.code)
          .filter(m => Option(m.group(1)).getOrElse(m.group(2)) != "Pure")
          .map(_.start)
          .toList
        counted("client-refined", s, "a refined constraint in a client", hits)

    private val Buckets = Set(
      "com.histmersion.db",
      "com.histmersion.files",
      "com.histmersion.values",
      "com.histmersion.upstreams"
    )

    private def bucket(s: SourceFile): List[Violation] =
      if Buckets(s.pkg) then counted("bucket", s, s"package ${s.pkg}", List(0)) else Nil

    val ProblemFile = "problem:util/problem/Problem.scala"

    private val CaseWithFields = "\\bcase\\s+(?:class\\s+)?([A-Z]\\w*)\\s*\\(".r
    private val PrimitiveField =
      "^(?:(?:Option|List|Set|Seq|Vector|NonEmptyVector)\\[)?(?:String|Int|Long|Double|Boolean|Legacy\\.Text)\\]?$".r

    private val Unrecognisers: Set[String] = Set(ProblemFile, "api:api/problem/StoredShapes.scala")
    private val Unrecognised = "\\bUnrecognised\\s*\\(".r

    private val FirstProblem =
      ("\\.toNonEmpty\\s*\\.\\s*head\\b|\\b[pP]roblems\\s*\\.\\s*head(?:Option)?\\b|" +
        "\\.sentences\\s*\\.\\s*head(?:Option)?\\b|\\bNonEmptyVector\\.toVector\\([^)\\n]*\\)\\s*\\.\\s*head(?:Option)?\\b").r

    private val FirstProblemPickers: Set[String] = Set("api:api/problem/Answer.scala")

    private val HtmlSinkWrite =
      "\\.(?:innerHTML|outerHTML)\\s*=(?!=)|\\binsertAdjacentHTML\\s*\\(|\\.setHTML\\s*\\(|\\bdocument\\.write(?:ln)?\\s*\\(".r

    private val HtmlSinkFile = "web:web/HtmlSink.scala"

    private val HtmlNewtype =
      "\\bobject\\s+\\w*Html\\s+extends\\s+(?:Newtype|Refined(?:Sub)?Type)\\s*\\[\\s*String\\b".r

    private val SchemaDerivation = "(?m)^(?![ \\t]*import\\b).*?\\bKindlingsSchema\\b".r
    private val ConfigGiven = "\\bgiven\\b[^=\\n]*\\bJsoniterConfig\\b".r
    private val TheConfig = "\\bgiven\\b[^=\\n]*\\bJsoniterConfig\\s*=\\s*JsonCodec\\.schemas\\b".r
    private val ConfigImport =
      "(?m)^[ \\t]*(?:import|export)\\s+(?:com\\.histmersion\\.)?(?:api|util\\.mcp\\.ToolInputs)\\.given\\b".r
    private val PackageClause = "(?m)^package\\s+([\\w.]+)[ \\t]*$".r

    def packagesInScope(code: String): List[String] =
      PackageClause
        .findAllMatchIn(code)
        .map(_.group(1))
        .toList
        .scanLeft("")((outer, p) => if outer.isEmpty then p else s"$outer.$p")
        .drop(1)

    def schemaConfigMisses(code: String, configHere: Boolean): List[Int] =
      val seen = configHere || ConfigImport.findFirstIn(code).isDefined
      val unseen = if seen then Nil else SchemaDerivation.findFirstMatchIn(code).map(_.start).toList
      val divergent = ConfigGiven
        .findAllMatchIn(code)
        .map(_.start)
        .toList
        .filterNot(at => TheConfig.findPrefixOf(code.substring(at)).isDefined)
      (unseen ++ divergent).sorted

    private val FailureCase = "\\bcase\\s+(?:scala\\.util\\.|util\\.)?Failure\\s*\\(".r
    private val FailureBlock =
      "\\.(?:recover|recoverWith|fallbackTo)\\s*[({]|\\.failed\\s*\\.\\s*(?:foreach|map|flatMap)\\s*[({]".r

    private val RequestCall = "\\bApiClient\\s*\\.\\s*[a-z]\\w*".r

    def failureRequests(code: String): List[Int] =
      val cases = FailureCase.findAllMatchIn(code).map(m => (m.start, caseEnd(code, m.start)))
      val blocks = FailureBlock.findAllMatchIn(code).map(m => (m.end - 1, blockEnd(code, m.end - 1)))
      (cases ++ blocks).toList
        .flatMap((from, to) => RequestCall.findAllMatchIn(code.substring(from, to)).map(from + _.start))
        .distinct
        .sorted

    private val AskCall = "\\.(?:ask|askThen)\\s*\\(".r

    private val NoRequest = "\\bApiClient\\s*\\.\\s*tileTemplate\\b".r

    private val RequestFiles: Set[String] = Set("web:web/ApiClient.scala", "web:web/Request.scala")

    def rawRequests(code: String): List[Int] =
      val asked = AskCall.findAllMatchIn(code).map(m => (m.end - 1, blockEnd(code, m.end - 1))).toList
      val pure = NoRequest.findAllMatchIn(code).map(_.start).toSet
      RequestCall
        .findAllMatchIn(code)
        .map(_.start)
        .filterNot(i => pure(i) || asked.exists((from, to) => i > from && i < to))
        .toList

    private def blockEnd(code: String, open: Int): Int =
      @tailrec def loop(i: Int, depth: Int): Int =
        if i >= code.length then code.length
        else
          code.charAt(i) match
            case '(' | '{' | '['               => loop(i + 1, depth + 1)
            case ')' | '}' | ']' if depth == 1 => i + 1
            case ')' | '}' | ']'               => loop(i + 1, depth - 1)
            case _                             => loop(i + 1, depth)
      loop(open, 0)

    private def caseEnd(code: String, at: Int): Int =
      val lineStart = code.lastIndexOf('\n', at) + 1
      val indent = code.substring(lineStart, at).takeWhile(_ == ' ').length
      @tailrec def loop(i: Int, depth: Int, lineHead: Boolean, col: Int): Int =
        if i >= code.length then code.length
        else
          val c = code.charAt(i)
          if c == '\n' then loop(i + 1, depth, lineHead = true, 0)
          else if lineHead && c == ' ' then loop(i + 1, depth, lineHead = true, col + 1)
          else if lineHead && depth == 0 && col <= indent then i
          else
            c match
              case '(' | '{' | '['               => loop(i + 1, depth + 1, lineHead = false, col)
              case ')' | '}' | ']' if depth == 0 => i
              case ')' | '}' | ']'               => loop(i + 1, depth - 1, lineHead = false, col)
              case _                             => loop(i + 1, depth, lineHead = false, col)
      loop(at + 1, 0, lineHead = false, 0)

    private val Relayers: Set[String] = Set(
      ProblemFile,
      "api:api/problem/Answer.scala",
      "api:scalajs:api/problem/ProblemJson.scala"
    )
    private val Relayed = "\\bRelayed\\s*\\(".r

    private def inPattern(code: String, at: Int): Boolean =
      val start = code.lastIndexOf('\n', at) + 1
      val end = code.indexOf('\n', at) match
        case -1 => code.length
        case n  => n
      val line = code.substring(start, end)
      line.trim.startsWith("case ") && line.indexOf("=>") > at - start

    private val UnclassifiedSite = "\\b(Unclassified|legacyText|Legacy)\\b".r

    def caseDefaults(sources: List[SourceFile]): List[Violation] =
      sources.flatMap { s =>
        CaseWithFields.findAllMatchIn(s.code).toList.flatMap { m =>
          val close = (m.end until s.code.length)
            .foldLeft((1, -1)) { case ((depth, at), i) =>
              if at >= 0 then (depth, at)
              else
                s.code(i) match
                  case '(' => (depth + 1, at)
                  case ')' => if depth == 1 then (0, i) else (depth - 1, at)
                  case _   => (depth, at)
            }
            ._2
          val params = if close < 0 then "" else s.code.substring(m.end, close)
          Param
            .findAllMatchIn(params)
            .filter(_.group(2).contains('='))
            .map(p => one("default", s, s"${m.group(1)}.${p.group(1)}", m.start))
            .toList
        }
      }

    def unclassified(sources: List[SourceFile]): List[Violation] =
      sources.flatMap(s =>
        UnclassifiedSite
          .findAllMatchIn(s.code)
          .map(m => one("unclassified", s, m.group(1), m.start))
      )

    def caseFields(code: String): List[(String, String, String, Int)] =
      CaseWithFields
        .findAllMatchIn(code)
        .toList
        .flatMap { m =>
          val close = code.indexOf(')', m.end)
          val params = if close < 0 then "" else code.substring(m.end, close)
          Param
            .findAllMatchIn(params)
            .map(p => (m.group(1), p.group(1), p.group(2).takeWhile(_ != '=').trim, m.start))
            .toList
        }

    private def problemFields(sources: List[SourceFile]): List[Violation] =
      sources
        .filter(_.key == ProblemFile)
        .flatMap(s =>
          caseFields(s.code).collect {
            case (name, field, tpe, at) if PrimitiveField.matches(tpe) =>
              one("problem-field", s, s"$name.$field: $tpe", at)
          }
        )

    val Components: List[String] = List(
      "Service",
      "Repo",
      "Controllers",
      "Controller",
      "Routes",
      "Tools",
      "Endpoints",
      "Endpoint",
      "Api",
      "Markdown",
      "Wiring",
      "Codecs",
      "Codec",
      "Client",
      "Fixtures",
      "JobKinds",
      "Jobs",
      "Data",
      "MergeRules",
      "Rules",
      "Referrer",
      "Carried",
      "Exchange",
      "Workflows",
      "Workflow",
      "Sql",
      "Plan",
      "Site",
      "Plugin",
      "Collection",
      "Search",
      "Imports",
      "Importer",
      "Importers",
      "Policy",
      "Pipeline",
      "Contributions",
      "Reader",
      "Writer",
      "Finder",
      "Resolver",
      "Converter",
      "Transport",
      "Store",
      "Cmd",
      "Views",
      "Html",
      "Page",
      "Pages"
    )
    private val Word = "[A-Z][a-z0-9]".r

    def propertyOf(name: String, entities: Set[String]): Option[String] =
      (1 until name.length).iterator
        .filter(i => Word.matches(name.substring(i, math.min(name.length, i + 2))))
        .map(i => (name.take(i), name.drop(i)))
        .find((entity, property) => entities(entity) && !Components.exists(property.endsWith) && property != "Rule")
        .map(_._1)

    private def prefix(sources: List[SourceFile]): List[Violation] =
      val defs = sources.flatMap(s => topLevel(s).map((_, name, at) => ((layerOf(module(s)), s.pkg), name, s, at)))
      val byPackage = defs.groupBy(_._1).view.mapValues(_.map(_._2).toSet).toMap
      defs
        .filterNot(d => module(d._3) == "web")
        .flatMap { (place, name, s, at) =>
          propertyOf(name, byPackage(place)).map(entity =>
            one("prefix", s, s"$name → $entity.${name.drop(entity.length)}", at)
          )
        }
        .distinctBy(_.row)
