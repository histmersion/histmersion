package com.histmersion.api.exchange
import com.histmersion.util.data.exchange.DecisionKind
import com.histmersion.api.given
import com.histmersion.util.data.exchange.Issue

object MergeResolutionsText:

  def flag(s: String): Either[String, MergeResolution] =
    s.indexOf('=') match
      case i if i > 0 && i < s.length - 1 => Right(MergeResolution(s.take(i).trim, s.drop(i + 1).trim))
      case _                              => Left(s"--resolve $s: expected ID=decision, e.g. S-3c1e7a2b=keep-left")

  def template(p: MergePlan, current: MergeResolutions): String =
    val chosen = current.resolve.map(r => r.issue -> r.decision).toMap
    def opts(i: MergeIssue): String =
      i.options
        .map { k =>
          val arg = (k, i.suggested) match
            case (_, Some(s)) if s.kind == k && s.argument.isDefined => s":${s.argument.get}"
            case (DecisionKind.Map, _) => s":${i.left.map(_.label).getOrElse("<citekey>")}"
            case _ if k.takesArgument  => ":<value>"
            case _                     => ""
          DecisionKind.wireOf(k) + arg
        }
        .mkString(" | ")
    def line(i: MergeIssue): String =
      val what = s"${i.entity.wire} — ${i.title}".replace("\n", " ")
      val cond = i.condition.fold("")(c => s"if ${c.issue} = ${DecisionKind.wireOf(c.when)}: ")
      (chosen.get(i.id), i.condition, i.severity) match
        case (Some(d), _, _)    => s"  ${i.id}: $d    # $cond$what"
        case (None, Some(_), _) =>
          s"  # ${i.id}: ${i.suggested.fold("")(_.wire)}    # $cond$what; decide ${opts(i)}"
        case (None, None, Issue.Severity.Default) =>
          s"  # ${i.id}: ${i.decision.orElse(i.suggested).fold("")(_.wire)}    # default — $what; decide ${opts(i)}"
        case (None, None, _) if i.decidedBy.contains(Issue.DecidedBy.Policy) =>
          s"  # ${i.id}: ${i.decision.fold("")(_.wire)}    # by policy — $what"
        case (None, None, _) =>
          s"  ${i.id}:    # $what; decide ${opts(i)}" + i.suggested.fold("")(s => s" (suggested: ${s.wire})")
    val issues = p.issues.filter(_.severity != Issue.Severity.Info)
    List(
      List(
        s"# resolutions for bundle ${p.bundleId} from ${p.from.label.getOrElse(p.from.id.toString)} — plan ${p.planHash}",
        "# left = this collection (here), right = the bundle. An id with no value is still open.",
        "# Use with: histmersion merge plan <bundle> --resolutions <this file>",
        s"bundle: ${p.bundleId}",
        "policy:",
        current.policy.editions.fold("  # editions: merge        # every year-gap question at once: merge | separate")(
          e => s"  editions: ${e.toString.toLowerCase}"
        ),
        if issues.isEmpty then "resolve: {}" else "resolve:"
      ),
      issues.map(line),
      texts(current)
    ).flatten.mkString("", "\n", "\n")

  /** YAML literal blocks: `|-` when the text has no final newline, `|` with one, `|+` with more, so the text reads back
    * exactly.
    */
  def texts(current: MergeResolutions): List[String] =
    val withText = current.resolve.flatMap(r => r.text.map(r.issue -> _)).sortBy(_._1)
    if withText.isEmpty then Nil
    else
      "texts:" :: withText.flatMap { (id, t) =>
        val chomp = if !t.endsWith("\n") then "-" else if t.endsWith("\n\n") then "+" else ""
        val lines = t.stripSuffix("\n").split("\n", -1).toList
        // with `|+` the empty last lines are the text's own trailing breaks
        s"  $id: |2$chomp" :: lines.map(l => if l.isEmpty then "" else s"    $l")
      }
