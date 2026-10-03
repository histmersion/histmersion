package com.histmersion.core

import org.yaml.snakeyaml.{LoaderOptions, Yaml}
import org.yaml.snakeyaml.constructor.SafeConstructor
import scala.jdk.CollectionConverters.*

/** The resolution file of `merge plan --resolutions` (docs/research-merge-import.md §6.3): read it,
  * and write the template `--write-template` starts one from. Pure.
  *
  * {{{
  * # resolutions.yaml — plan 7c41e9a2 · left = this collection, right = the bundle
  * bundle: 0192f6c4-…
  * policy:
  *   editions: separate          # every year-gap question at once: merge | separate
  * resolve:
  *   S-3c1e7a2b: map:guterbock1997_perspectives
  *   A-17b0d2aa:                 # still open
  * }}}
  *
  * An id with no value is still open. Unknown keys are errors, so a typo never passes for a decision. */
object MergeResolutionsFile:

  private def yaml = Yaml(SafeConstructor(LoaderOptions()))

  def parse(text: String): Either[List[String], MergeResolutions] =
    scala.util.Try(Option(yaml.load[AnyRef](text))).toEither.left.map(e => List(s"not YAML: ${e.getMessage}")).flatMap {
      case None => Right(MergeResolutions())
      case Some(m: java.util.Map[?, ?]) =>
        val top = m.asScala.toMap.map((k, v) => String.valueOf(k) -> v.asInstanceOf[AnyRef])
        val errors = List.newBuilder[String]
        top.keySet.diff(Set("bundle", "plan", "policy", "resolve")).toList.sorted.foreach(k =>
          errors += s"unknown key '$k' (bundle, policy, resolve)")
        val bundle = top.get("bundle").flatMap(Option(_)).map(String.valueOf).flatMap { b =>
          scala.util.Try(java.util.UUID.fromString(b.trim)).toOption.orElse { errors += s"bundle: '$b' is not a bundle id"; None }
        }
        val editions = top.get("policy").flatMap(Option(_)) match
          case None => None
          case Some(p: java.util.Map[?, ?]) =>
            val pm = p.asScala.toMap.map((k, v) => String.valueOf(k) -> v.asInstanceOf[AnyRef])
            pm.keySet.diff(Set("editions")).toList.sorted.foreach(k => errors += s"policy: unknown key '$k' (editions)")
            pm.get("editions").flatMap(Option(_)).map(String.valueOf).flatMap { e =>
              EditionsPolicy.values.find(_.toString.equalsIgnoreCase(e.trim))
                .orElse { errors += s"policy.editions: '$e' is not merge or separate"; None }
            }
          case Some(other) => errors += s"policy: expected a map, found '$other'"; None
        val resolve = top.get("resolve").flatMap(Option(_)) match
          case None => Nil
          case Some(r: java.util.Map[?, ?]) =>
            r.asScala.toList.flatMap { (k, v) =>
              Option(v).map(String.valueOf).map(_.trim).filter(_.nonEmpty).map(d => MergeResolution(String.valueOf(k).trim, d))
            }.sortBy(_.issue)
          case Some(other) => errors += s"resolve: expected a map of issue id → decision, found '$other'"; Nil
        val es = errors.result()
        if es.nonEmpty then Left(es) else Right(MergeResolutions(resolve, MergePolicy(editions), bundle))
      case Some(other) => Left(List(s"expected a map at the top, found '$other'"))
    }

  /** `ID=decision` of a `--resolve` flag. */
  def flag(s: String): Either[String, MergeResolution] =
    s.indexOf('=') match
      case i if i > 0 && i < s.length - 1 => Right(MergeResolution(s.take(i).trim, s.drop(i + 1).trim))
      case _                              => Left(s"--resolve $s: expected ID=decision, e.g. S-3c1e7a2b=keep-left")

  /** The template: every decided issue with its decision, every open one empty with its options as a
    * comment, every conditional or defaulted one commented out with what it would be. */
  def template(p: MergePlan, current: MergeResolutions): String =
    val chosen = current.resolve.map(r => r.issue -> r.decision).toMap
    def opts(i: MergeIssue): String =
      i.options.map { k =>
        val arg = (k, i.suggested) match
          case (_, Some(s)) if s.kind == k && s.argument.isDefined => s":${s.argument.get}"
          case (DecisionKind.Map, _) => s":${i.left.map(_.label).getOrElse("<citekey>")}"
          case _ if k.takesArgument  => ":<value>"
          case _                     => ""
        DecisionKind.wireOf(k) + arg
      }.mkString(" | ")
    def line(i: MergeIssue): String =
      val what = s"${i.entity.wire} — ${i.title}".replace("\n", " ")
      val cond = i.condition.fold("")(c => s"if ${c.issue} = ${DecisionKind.wireOf(c.when)}: ")
      (chosen.get(i.id), i.condition, i.severity) match
        case (Some(d), _, _) => s"  ${i.id}: $d    # $cond$what"
        case (None, Some(_), _) =>
          s"  # ${i.id}: ${i.suggested.fold("")(_.wire)}    # $cond$what; decide ${opts(i)}"
        case (None, None, IssueSeverity.Default) =>
          s"  # ${i.id}: ${i.decision.orElse(i.suggested).fold("")(_.wire)}    # default — $what; decide ${opts(i)}"
        case (None, None, _) if i.decidedBy.contains(DecidedBy.Policy) =>
          s"  # ${i.id}: ${i.decision.fold("")(_.wire)}    # by policy — $what"
        case (None, None, _) =>
          s"  ${i.id}:    # $what; decide ${opts(i)}" + i.suggested.fold("")(s => s" (suggested: ${s.wire})")
    val issues = p.issues.filter(_.severity != IssueSeverity.Info)
    List(
      List(s"# resolutions for bundle ${p.bundleId} from ${p.from.label.getOrElse(p.from.id.toString)} — plan ${p.planHash}",
        "# left = this collection (here), right = the bundle. An id with no value is still open.",
        "# Use with: histmersion merge plan <bundle> --resolutions <this file>",
        s"bundle: ${p.bundleId}",
        "policy:",
        current.policy.editions.fold("  # editions: merge        # every year-gap question at once: merge | separate")(e =>
          s"  editions: ${e.toString.toLowerCase}"),
        if issues.isEmpty then "resolve: {}" else "resolve:"),
      issues.map(line)
    ).flatten.mkString("", "\n", "\n")
