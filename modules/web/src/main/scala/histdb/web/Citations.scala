package histdb.web

import org.scalajs.dom
import org.scalajs.dom.{document, window}

import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object Citations:

  private case class CiteEntry(
      var n: Int,
      var occ: Int,
      label: Option[String],
      fullText: String,
      var anchor: Option[String],
      var firstRef: Option[String]
  )

  private var tip: Option[dom.Element] = None
  private val sourceCache = scala.collection.mutable.Map.empty[String, js.Dynamic]

  def process(root: dom.Element): Unit =
    if root == null then ()
    else
      val footerStart = findFooterStart(root)
      val sourcesList = findSourcesList(root)
      sourcesList.foreach(sl => processSourceCitations(root, footerStart, sl))
      processArticleLinks(root)

  private def processSourceCitations(root: dom.Element, footerStart: Option[dom.Element], sourcesList: dom.Element): Unit =
    val citeMap = scala.collection.mutable.LinkedHashMap.empty[String, CiteEntry]

    val lis = sourcesList.querySelectorAll("li")
    var i = 0
    while i < lis.length do
      val li = lis(i).asInstanceOf[dom.Element]
      val a = li.querySelector("a[href^=\"/source/\"]")
      if a != null then
        ckFrom(a.getAttribute("href")).foreach { ck =>
          val fullText = liTextWithoutCk(li, ck)
          val label = buildLabel(fullText)
          citeMap(ck) = CiteEntry(0, 0, label, fullText, None, None)
        }
      i += 1

    val bodyLinks = scala.collection.mutable.ListBuffer.empty[dom.Element]
    var num = 0
    val allLinks = root.querySelectorAll("a[href^=\"/source/\"]")
    var j = 0
    while j < allLinks.length do
      val a = allLinks(j).asInstanceOf[dom.Element]
      val inFooter = footerStart.exists { fs =>
        (fs.compareDocumentPosition(a) & dom.Node.DOCUMENT_POSITION_FOLLOWING) != 0
      }
      if !inFooter then
        bodyLinks += a
        ckFrom(a.getAttribute("href")).foreach { ck =>
          citeMap.get(ck).foreach { entry =>
            if entry.n == 0 then
              num += 1
              entry.n = num
          }
        }
      j += 1

    if num == 0 then ()
    else
      for (ck, entry) <- citeMap if entry.n > 0 do
        findLiForCk(sourcesList, ck).foreach { li =>
          li.asInstanceOf[dom.HTMLElement].id = "cite-" + entry.n
          entry.anchor = Some("cite-" + entry.n)
        }

      for a <- bodyLinks do
        ckFrom(a.getAttribute("href")).foreach { ck =>
          citeMap.get(ck).foreach { entry =>
            entry.anchor.foreach { anchor =>
              val next = a.asInstanceOf[dom.HTMLElement].nextElementSibling
              val alreadyProcessed = next != null && next.classList.contains("cite-num")
              if !alreadyProcessed then
                entry.occ += 1
                val refId = s"cite-ref-${entry.n}-${entry.occ}"
                a.asInstanceOf[dom.HTMLElement].id = refId
                if entry.firstRef.isEmpty then entry.firstRef = Some(refId)

                entry.label.foreach(lbl => a.textContent = lbl)
                a.classList.add("cite-source")

                val sup = document.createElement("sup").asInstanceOf[dom.HTMLElement]
                sup.className = "cite-num"
                val link = document.createElement("a").asInstanceOf[dom.HTMLAnchorElement]
                link.href = "#" + anchor
                link.textContent = s"[${entry.n}]"
                link.className = "cite-jump"
                link.addEventListener("click", jumpTo(root, anchor))
                sup.appendChild(link)
                a.asInstanceOf[dom.HTMLElement].after(sup)

                a.asInstanceOf[dom.HTMLElement].dataset.update("ck", ck)
                a.asInstanceOf[dom.HTMLElement].dataset.update("citeFallback", entry.fullText)
                a.addEventListener("mouseenter", showSourceTip(_))
                a.addEventListener("mouseleave", (_: dom.MouseEvent) => hideTip())
            }
          }
        }

      for (_, entry) <- citeMap do
        for anchor <- entry.anchor; firstRef <- entry.firstRef do
          Option(root.querySelector("#" + anchor)).foreach { li =>
            val back = document.createElement("a").asInstanceOf[dom.HTMLAnchorElement]
            back.href = "#" + firstRef
            back.className = "cite-back"
            back.textContent = "↑"
            back.title = "Jump back to citation"
            back.addEventListener("click", jumpTo(root, firstRef))
            li.insertBefore(back, li.firstChild)
          }

  private def processArticleLinks(root: dom.Element): Unit =
    val links = root.querySelectorAll("a[href^=\"/article/\"]")
    if links.length == 0 then ()
    else
      val slugSet = scala.collection.mutable.LinkedHashSet.empty[String]
      var i = 0
      while i < links.length do
        slugFrom(links(i).asInstanceOf[dom.Element].getAttribute("href")).foreach { slug =>
          if slug != "index" then slugSet += slug
        }
        i += 1

      if slugSet.isEmpty then ()
      else
        fetchArticleTitles(slugSet.toList).foreach { titles =>
          var k = 0
          while k < links.length do
            val a = links(k).asInstanceOf[dom.Element]
            slugFrom(a.getAttribute("href")).foreach { slug =>
              titles.get(slug).foreach { info =>
                if a.textContent.trim == slug then a.textContent = info._1
                a.classList.add("cite-article")
                a.asInstanceOf[dom.HTMLElement].dataset.update("articleInfo", info._2.getOrElse(info._1))
                a.addEventListener("mouseenter", showArticleTip(_))
                a.addEventListener("mouseleave", (_: dom.MouseEvent) => hideTip())
              }
            }
            k += 1
        }

  private def fetchArticleTitles(slugs: List[String]): scala.concurrent.Future[Map[String, (String, Option[String])]] =
    import histdb.core.*
    ApiClient.articles(Api.ArticleQuery(limit = Some(200))).map { list =>
      list.articles.collect {
        case a if slugs.contains(a.slug) => a.slug -> (a.title, a.summary)
      }.toMap
    }.recover { case _ => Map.empty }

  private def ckFrom(href: String): Option[String] =
    if href == null then None
    else
      val m = """^/source/([a-z0-9_.%-]+)""".r.findFirstMatchIn(href)
      m.map(g => js.URIUtils.decodeURIComponent(g.group(1)))

  private def slugFrom(href: String): Option[String] =
    if href == null then None
    else
      val m = """^/article/([a-z0-9_.%-]+)""".r.findFirstMatchIn(href)
      m.map(g => js.URIUtils.decodeURIComponent(g.group(1)))

  private def findFooterStart(root: dom.Element): Option[dom.Element] =
    val pat = "(?i)^(sources|backlinks|not used)".r
    val headings = root.querySelectorAll("h2")
    var i = 0
    while i < headings.length do
      val h = headings(i).asInstanceOf[dom.Element]
      if pat.findFirstIn(h.textContent.trim).isDefined then
        return Some(h)
      i += 1
    None

  private def findSourcesList(root: dom.Element): Option[dom.Element] =
    val headings = root.querySelectorAll("h2")
    var i = 0
    while i < headings.length do
      val h = headings(i).asInstanceOf[dom.Element]
      if "(?i)^sources".r.findFirstIn(h.textContent.trim).isDefined then
        var el = h.nextElementSibling
        while el != null && el.tagName != "UL" && el.tagName != "H2" do
          el = el.nextElementSibling
        if el != null && el.tagName == "UL" then return Some(el)
      i += 1
    None

  private def findLiForCk(ul: dom.Element, ck: String): Option[dom.Element] =
    val lis = ul.querySelectorAll("li")
    var i = 0
    while i < lis.length do
      val li = lis(i).asInstanceOf[dom.Element]
      val a = li.querySelector("a[href^=\"/source/\"]")
      if a != null && ckFrom(a.getAttribute("href")).contains(ck) then
        return Some(li)
      i += 1
    None

  private def liTextWithoutCk(li: dom.Element, ck: String): String =
    val raw = new StringBuilder
    val nodes = li.childNodes
    var i = 0
    while i < nodes.length do
      raw.append(nodes(i).textContent)
      i += 1
    val escaped = ck.replaceAll("[.*+?^${}()|\\[\\]\\\\]", "\\\\$0")
    raw.toString.replaceFirst(s"(?i)^$escaped\\s*—\\s*", "").trim

  private def buildLabel(footerText: String): Option[String] =
    val parts = footerText.split(" · ")
    if parts.length >= 2 then
      val authYr = parts(0).trim
      val titleRaw = parts(1).trim
      val title = if titleRaw.length > 50 then titleRaw.substring(0, 47) + "…" else titleRaw
      Some(s"$authYr, $title")
    else
      val main = if footerText.length > 70 then footerText.substring(0, 67) + "…" else footerText
      if main.nonEmpty then Some(main) else None

  private def jumpTo(root: dom.Element, anchorId: String): js.Function1[dom.Event, Unit] =
    (e: dom.Event) =>
      e.preventDefault()
      e.stopPropagation()
      Option(root.querySelector("#" + anchorId)).foreach { target =>
        target.asInstanceOf[js.Dynamic].scrollIntoView(
          js.Dynamic.literal(behavior = "smooth", block = "center")
        )
      }

  private def showSourceTip(e: dom.MouseEvent): Unit =
    hideTip()
    val a = e.currentTarget.asInstanceOf[dom.HTMLElement]
    val ck = a.dataset.get("ck").getOrElse("")
    if ck.isEmpty then ()
    else
      val fallback = a.dataset.get("citeFallback").getOrElse("")
      renderTip(a, formatSourceFallback(fallback))

      sourceCache.get(ck) match
        case Some(data) => renderTip(a, formatSourceFull(data))
        case None =>
          ApiClient.fetchHtml(s"/api/sources/${js.URIUtils.encodeURIComponent(ck)}").foreach { body =>
            try
              val data = js.JSON.parse(body)
              sourceCache(ck) = data
              if tip.isDefined && a.matches(":hover") then renderTip(a, formatSourceFull(data))
            catch case _: Throwable => ()
          }

  private def showArticleTip(e: dom.MouseEvent): Unit =
    hideTip()
    val a = e.currentTarget.asInstanceOf[dom.HTMLElement]
    val info = a.dataset.get("articleInfo").getOrElse("")
    if info.nonEmpty then
      renderTip(a, s"""<div class="cite-tip-desc">${Fmt.esc(info)}</div>""")

  private def renderTip(anchor: dom.Element, html: String): Unit =
    hideTip()
    val el = document.createElement("div")
    el.asInstanceOf[dom.HTMLElement].className = "cite-tooltip"
    el.innerHTML = html
    document.body.appendChild(el)
    tip = Some(el)

    val rect = anchor.getBoundingClientRect()
    val maxW = math.min(440, window.innerWidth.toInt - 20)
    el.asInstanceOf[dom.HTMLElement].style.maxWidth = maxW + "px"

    var left = rect.left + rect.width / 2 - maxW / 2
    left = math.max(10, math.min(left, window.innerWidth - maxW - 10))
    el.asInstanceOf[dom.HTMLElement].style.left = left + "px"
    el.asInstanceOf[dom.HTMLElement].style.top = (rect.bottom + 6) + "px"

    window.requestAnimationFrame { _ =>
      tip.foreach { t =>
        val tr = t.getBoundingClientRect()
        if tr.bottom > window.innerHeight - 10 then
          t.asInstanceOf[dom.HTMLElement].style.top = (rect.top - tr.height - 6) + "px"
      }
    }

  private def formatSourceFallback(text: String): String =
    if text.isEmpty then ""
    else
      val parts = text.split(" · ")
      val sb = new StringBuilder
      sb.append(s"""<div class="cite-tip-meta">${Fmt.esc(parts.headOption.getOrElse(""))}</div>""")
      if parts.length > 1 then sb.append(s"""<div class="cite-tip-title">${Fmt.esc(parts(1))}</div>""")
      if parts.length > 2 then sb.append(s"""<div class="cite-tip-status ${Fmt.esc(parts(2).toLowerCase)}">${Fmt.esc(parts(2))}</div>""")
      sb.toString

  private def toList(v: js.Dynamic): List[String] =
    if v == null || js.isUndefined(v) then Nil
    else if js.Array.isArray(v) then
      v.asInstanceOf[js.Array[js.Dynamic]].toList.collect {
        case x if x != null && !js.isUndefined(x) => x.toString
      }.filter(_.nonEmpty)
    else
      v.toString.split(",\\s*").map(_.trim).filter(_.nonEmpty).toList

  private def formatSourceFull(s: js.Dynamic): String =
    val src = if !js.isUndefined(s.source) && s.source != null then s.source else s
    val sb = new StringBuilder

    val title = if !js.isUndefined(src.title) && src.title != null then src.title.toString else ""
    sb.append(s"""<div class="cite-tip-title">${Fmt.esc(title)}</div>""")

    val meta = List(
      toList(src.authors).mkString(", "),
      if !js.isUndefined(src.year) && src.year != null then src.year.toString else "",
      if !js.isUndefined(src.venue) && src.venue != null then src.venue.toString else ""
    ).filter(_.nonEmpty).mkString(" · ")
    if meta.nonEmpty then sb.append(s"""<div class="cite-tip-meta">${Fmt.esc(meta)}</div>""")

    val topicChips = toList(src.topics)
    val tagChips = toList(src.tags)
    val allChips = topicChips ++ tagChips
    if allChips.nonEmpty then
      sb.append("""<div class="cite-tip-chips">""")
      topicChips.foreach(c => sb.append(s"""<span class="cite-chip" data-filter-key="topics" data-filter-value="${Fmt.esc(c)}">${Fmt.esc(Fmt.display(c))}</span> """))
      tagChips.foreach(c => sb.append(s"""<span class="cite-chip" data-filter-key="tags" data-filter-value="${Fmt.esc(c)}">${Fmt.esc(Fmt.display(c))}</span> """))
      sb.append("</div>")

    val geo = List(
      toList(src.regions).mkString(", "),
      if !js.isUndefined(src.periodStart) && src.periodStart != null &&
         !js.isUndefined(src.periodEnd) && src.periodEnd != null then
        Fmt.periodStr(src.periodStart.asInstanceOf[Number].longValue, src.periodEnd.asInstanceOf[Number].longValue)
      else ""
    ).filter(_.nonEmpty).mkString(" · ")
    if geo.nonEmpty then sb.append(s"""<div class="cite-tip-geo">${Fmt.esc(geo)}</div>""")

    val status = if !js.isUndefined(src.status) && src.status != null then src.status.toString else ""
    if status.nonEmpty then sb.append(s"""<div class="cite-tip-status ${Fmt.esc(status)}">${Fmt.esc(status)}</div>""")

    val summary = if !js.isUndefined(src.summary) && src.summary != null then src.summary.toString else ""
    if summary.nonEmpty then
      val short = if summary.length > 300 then summary.substring(0, 297) + "…" else summary
      sb.append(s"""<div class="cite-tip-summary">${Fmt.esc(short)}</div>""")

    sb.toString

  private def hideTip(): Unit =
    tip.foreach(_.remove())
    tip = None
