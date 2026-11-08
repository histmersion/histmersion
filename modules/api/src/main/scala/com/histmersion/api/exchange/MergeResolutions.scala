package com.histmersion
package api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID
import com.histmersion.api.given
import io.github.iltotore.iron.constraint.any.Pure

final case class MergeResolutions(
    resolve: List[MergeResolution] = Nil,
    policy: MergePolicy = MergePolicy(),
    bundle: Option[UUID] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema, com.histmersion.api.json.Schema:
  def overriddenBy(later: MergeResolutions): MergeResolutions =
    val ids = later.resolve.map(_.issue).toSet
    MergeResolutions(
      resolve.filterNot(r => ids(r.issue)) ++ later.resolve,
      MergePolicy(later.policy.editions.orElse(policy.editions)),
      later.bundle.orElse(bundle)
    )
object MergeResolutions:


  object Text:
    type Flag = Flag.T
    object Flag extends Newtype[String, Pure]
    type Problem = Problem.T
    object Problem extends Newtype[String, Pure]
    type Template = Template.T
    object Template extends Newtype[String, Pure]



    def flag(s: Flag): Either[Problem, MergeResolution] =
      (s).value.indexOf('=') match
        case i if i > 0 && i < (s).value.length - 1 => Right(MergeResolution(MergeResolution.Issue((s).value.take(i).trim), MergeDecision.Kind.Wire((s).value.drop(i + 1).trim)))
        case _                              => Left(Problem(s"--resolve $s: expected ID=decision, e.g. S-3c1e7a2b=keep-left"))

    def template(p: MergePlan, current: MergeResolutions): Template =
      val chosen = current.resolve.map(r => r.issue.value -> r.decision).toMap
      def opts(i: MergeIssue): String =
        i.options
          .map { k =>
            val arg = (k, i.suggested) match
              case (_, Some(s)) if s.kind == k && s.argument.isDefined => s":${s.argument.get}"
              case (MergeDecision.Kind.Map, _) => s":${i.left.map(_.label).getOrElse("<citekey>")}"
              case _ if ((k.takesArgument) == (true))        => ":<value>"
              case _                           => ""
            MergeDecision.Kind.wireOf(k).value + arg
          }
          .mkString(" | ")
      def line(i: MergeIssue): String =
        val what = s"${i.entity.wire} — ${i.title}".replace("\n", " ")
        val cond = i.condition.fold("")(c => s"if ${c.issue} = ${MergeDecision.Kind.wireOf(c.when)}: ")
        (chosen.get(i.id.value), i.condition, i.severity) match
          case (Some(d), _, _)    => s"  ${i.id}: $d    # $cond$what"
          case (None, Some(_), _) =>
            s"  # ${i.id}: ${i.suggested.fold("")(_.wire)}    # $cond$what; decide ${opts(i)}"
          case (None, None, MergeIssue.Severity.Default) =>
            s"  # ${i.id}: ${i.decision.orElse(i.suggested).fold("")(_.wire)}    # default — $what; decide ${opts(i)}"
          case (None, None, _) if i.decidedBy.contains(MergeIssue.DecidedBy.Policy) =>
            s"  # ${i.id}: ${i.decision.fold("")(_.wire)}    # by policy — $what"
          case (None, None, _) =>
            s"  ${i.id}:    # $what; decide ${opts(i)}" + i.suggested.fold("")(s => s" (suggested: ${s.wire})")
      val issues = p.issues.filter(_.severity != MergeIssue.Severity.Info)
      Template(List(
        List(
          s"# resolutions for bundle ${p.bundleId} from ${p.from.label.getOrElse(p.from.id.toString)} — plan ${p.planHash}",
          "# left = this collection (here), right = the bundle. An id with no value is still open.",
          "# Use with: histmersion merge plan <bundle> --resolutions <this file>",
          s"bundle: ${p.bundleId}",
          "policy:",
          current.policy.editions.fold(
            "  # editions: merge        # every year-gap question at once: merge | separate"
          )(e => s"  editions: ${e.toString.toLowerCase}"),
          if issues.isEmpty then "resolve: {}" else "resolve:"
        ),
        issues.map(line),
        texts(current)
      ).flatten.mkString("", "\n", "\n"))

    /** YAML literal blocks: `|-` when the text has no final newline, `|` with one, `|+` with more, so the text reads
      * back exactly.
      */
    def texts(current: MergeResolutions): List[String] =
      val withText = current.resolve.flatMap(r => r.text.map(r.issue -> _)).sortBy(_._1)
      if withText.isEmpty then Nil
      else
        "texts:" :: withText.flatMap { (id, t) =>
          val chomp = if !t.value.endsWith("\n") then "-" else if t.value.endsWith("\n\n") then "+" else ""
          val lines = t.value.stripSuffix("\n").split("\n", -1).toList
          // with `|+` the empty last lines are the text's own trailing breaks
          s"  $id: |2$chomp" :: lines.map(l => if l.isEmpty then "" else s"    $l")
        }
