package com.histmersion.api.exchange
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

import java.util.UUID
import com.histmersion.api.given

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
    object Flag extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Problem = Problem.T
    object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Resolution = Resolution.T
    object Resolution extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Template = Template.T
    object Template extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TemplateLine = TemplateLine.T
    object TemplateLine extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type TemplateOptions = TemplateOptions.T
    object TemplateOptions extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]



    def flag(s: com.histmersion.api.exchange.MergeResolutions.Text.Flag): Either[com.histmersion.api.exchange.MergeResolutions.Text.Problem, MergeResolution] =
      (s).value.indexOf('=') match
        case i if i > 0 && i < (s).value.length - 1 => Right(MergeResolution(_root_.com.histmersion.api.exchange.MergeResolution.Issue((s).value.take(i).trim), _root_.com.histmersion.api.exchange.MergeResolution.Decision((s).value.drop(i + 1).trim)))
        case _                              => Left(_root_.com.histmersion.api.exchange.MergeResolutions.Text.Problem(s"--resolve $s: expected ID=decision, e.g. S-3c1e7a2b=keep-left"))

    def template(p: MergePlan, current: MergeResolutions): com.histmersion.api.exchange.MergeResolutions.Text.Template =
      val chosen = current.resolve.map(r => r.issue -> r.decision).toMap
      def opts(i: MergeIssue): com.histmersion.api.exchange.MergeResolutions.Text.TemplateOptions =
        i.options
          .map { k =>
            val arg = (k, i.suggested) match
              case (_, Some(s)) if s.kind == k && s.argument.isDefined => s":${s.argument.get}"
              case (MergeDecision.Kind.Map, _) => s":${i.left.map(_.label).getOrElse("<citekey>")}"
              case _ if ((k.takesArgument) == (true))        => ":<value>"
              case _                           => ""
            MergeDecision.Kind.wireOf(k) + arg
          }
          .mkString(" | ")
      def line(i: MergeIssue): com.histmersion.api.exchange.MergeResolutions.Text.TemplateLine =
        val what = s"${i.entity.wire} — ${i.title}".replace("\n", " ")
        val cond = i.condition.fold("")(c => s"if ${c.issue} = ${MergeDecision.Kind.wireOf(c.when)}: ")
        (chosen.get(i.id), i.condition, i.severity) match
          case (Some(d), _, _)    => s"  ${i.id}: $d    # $cond$what"
          case (None, Some(_), _) =>
            _root_.com.histmersion.api.exchange.MergeResolutions.Text.TemplateLine(s"  # ${i.id}: ${i.suggested.fold("")(_.wire)}    # $cond$what; decide ${opts(i)}")
          case (None, None, MergeIssue.Severity.Default) =>
            _root_.com.histmersion.api.exchange.MergeResolutions.Text.TemplateLine(s"  # ${i.id}: ${i.decision.orElse(i.suggested).fold("")(_.wire)}    # default — $what; decide ${opts(i)}")
          case (None, None, _) if i.decidedBy.contains(MergeIssue.DecidedBy.Policy) =>
            _root_.com.histmersion.api.exchange.MergeResolutions.Text.TemplateLine(s"  # ${i.id}: ${i.decision.fold("")(_.wire)}    # by policy — $what")
          case (None, None, _) =>
            _root_.com.histmersion.api.exchange.MergeResolutions.Text.TemplateLine(s"  ${i.id}:    # $what; decide ${opts(i)}" + i.suggested.fold("")(s => s" (suggested: ${s.wire})"))
      val issues = p.issues.filter(_.severity != MergeIssue.Severity.Info)
      _root_.com.histmersion.api.exchange.MergeResolutions.Text.Template(List(
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
    def texts(current: MergeResolutions): List[com.histmersion.api.exchange.MergeResolutions.Text.Resolution] =
      val withText = current.resolve.flatMap(r => r.text.map(r.issue -> _)).sortBy(_._1)
      if withText.isEmpty then Nil
      else
        _root_.com.histmersion.api.exchange.MergeResolutions.Text.Resolution("texts:") :: withText.flatMap { (id, t) =>
          val chomp = if !t.endsWith("\n") then "-" else if t.endsWith("\n\n") then "+" else ""
          val lines = t.stripSuffix("\n").split("\n", -1).toList
          // with `|+` the empty last lines are the text's own trailing breaks
          s"  $id: |2$chomp" :: lines.map(l => if l.isEmpty then "" else s"    $l")
        }
