package com.histmersion.api

/** An article's links to sources and other articles, over the wiki text's links (`util.text.WikiLinks`). */
object ArticleLinks:

  val TargetArticle = "article"
  val TargetSource = "source"

  export com.histmersion.util.text.WikiLinks.{isCitekeyShaped, normalize, occurrences, targets, Occurrence}

  def classify(targets: List[String], isSource: String => Boolean): List[ArticleLink] =
    targets.map { t =>
      if isCitekeyShaped(t) && isSource(t) then ArticleLink(TargetSource, t)
      else ArticleLink(TargetArticle, t)
    }

  def links(body: String, isSource: String => Boolean): List[ArticleLink] =
    classify(targets(body), isSource)

  def hrefFor(target: String, citekeyOf: String => Option[String], isArticle: String => Boolean): Option[String] =
    citekeyOf(target)
      .map(ck => s"/source/$ck")
      .orElse(Option.when(isArticle(target))(s"/article/$target"))

  def linkMarkdown(
      md: String,
      href: String => Option[String],
      displayLabel: String => Option[String] = _ => None
  ): String =
    rewriteLinks(md) { (t, explicit, written) =>
      val label = explicit.getOrElse(written)
      val text = if label.isEmpty then displayLabel(t).getOrElse(Label.display(t)) else label
      href(t) match
        case Some(h) => s"[$text]($h)"
        case None    => s"""<span class="dangling" title="unresolved link">$text</span>"""
    }

  export com.histmersion.util.text.WikiLinks.{retarget, rewriteLinks}

  def resolveMarkdown(md: String, isSource: String => Boolean): String =
    linkMarkdown(md, t => Some(if isCitekeyShaped(t) && isSource(t) then s"/source/$t" else s"/article/$t"))
