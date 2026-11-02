package com.histmersion.api.research
import com.histmersion.api.checks.Lint.*

/** The articles' lint rules: a thin or stand-in summary, filler phrases, uncited paragraphs, unpaired critiques. */
object ArticleLint:

  val FillerPhrases: List[String] = List(
    "it is worth noting",
    "it should be noted",
    "it is important to note",
    "interestingly",
    "notably",
    "significantly",
    "it is clear that",
    "needless to say",
    "of course",
    "as is well known",
    "it goes without saying",
    "in conclusion",
    "to summarize",
    "in summary",
    "broadly speaking",
    "generally speaking",
    "it must be emphasized",
    "it cannot be overstated",
    "plays a crucial role",
    "rich tapestry",
    "sheds light on",
    "shed new light",
    "paved the way",
    "remains to be seen",
    "further research is needed"
  )

  private[api] val FillerPattern: scala.util.matching.Regex =
    FillerPhrases.map(p => java.util.regex.Pattern.quote(p)).mkString("(?i)(?:", "|", ")").r

  def articleThinSummary(rows: List[(String, String)]): List[LintItem] =
    rows
      .flatMap { (slug, summary) =>
        val s = summary.trim
        val why =
          if s.isEmpty then Some("no summary")
          else
            Articles
              .summaryOf(Some(s))
              .left
              .toOption
              .map(_ => s"stand-in summary: \"$s\"")
              .orElse(Option.when(s.length < MinSummaryChars)(s"only ${s.length} chars: \"$s\""))
        why.map(LintItem(slug, _))
      }
      .sortBy(_.citekey)

  def articleFiller(articles: List[(String, String)]): List[LintItem] =
    articles
      .flatMap { (slug, body) =>
        val matches = FillerPattern.findAllMatchIn(body).map(_.matched.toLowerCase).toList.distinct
        if matches.isEmpty then None
        else
          Some(
            LintItem(
              slug,
              s"filler: ${matches.take(3).mkString(", ")}${
                  if matches.sizeIs > 3 then s" (+${matches.size - 3} more)" else ""
                }"
            )
          )
      }
      .sortBy(_.citekey)

  private[api] val WikilinkCitekey =
    """\[\[((?=[a-z0-9_]*(?:_|\d{4}))[a-z][a-z0-9_]*)""".r

  def articleUncitedParagraphs(articles: List[(String, String)]): List[LintItem] =
    articles
      .flatMap { (slug, body) =>
        val paras = extractParagraphs(body)
        val uncited = paras.filter(p => WikilinkCitekey.findFirstIn(p).isEmpty)
        if uncited.isEmpty then None
        else Some(LintItem(slug, s"${uncited.size} paragraph(s) with no [[citekey]]: \"${uncited.head.take(60)}…\""))
      }
      .sortBy(_.citekey)

  def articleCritiquePairing(
      articles: List[(String, String)],
      resolve: String => Option[String],
      edges: List[CoverageEdge],
      work: String => Option[CritiquePairing.Work]
  ): List[LintItem] =
    articles
      .flatMap((slug, body) => CritiquePairing.hits(body, resolve, edges, work).map(h => LintItem(slug, h.line)))
      .sortBy(_.citekey)
