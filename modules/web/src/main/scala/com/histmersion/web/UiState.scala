package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.core.*
import org.scalajs.dom
import org.scalajs.dom.window

import scala.scalajs.js
import scala.util.Try

object UiState:

  // Reactive variables — the Laminar equivalent of State.get()/set()
  val q: Var[Option[String]]         = Var(None)
  val tags: Var[List[String]]        = Var(Nil)
  val authors: Var[List[String]]     = Var(Nil)
  val places: Var[List[String]]      = Var(Nil)
  val placeIds: Var[List[String]]    = Var(Nil)
  val topics: Var[List[String]]      = Var(Nil)
  val status: Var[List[String]]      = Var(Nil)
  val yearFrom: Var[Option[Long]]    = Var(None)
  val yearTo: Var[Option[Long]]      = Var(None)
  val from: Var[Option[Long]]        = Var(None)
  val to: Var[Option[Long]]          = Var(None)
  val bbox: Var[Option[String]]      = Var(None)
  val inView: Var[Boolean]           = Var(false)
  val sort: Var[Option[String]]      = Var(None)
  val open: Var[Option[String]]      = Var(None)
  val tab: Var[Option[String]]       = Var(None)
  val aq: Var[Option[String]]        = Var(None)

  /** A hovered filter suggestion, applied to every query but never to the chips or the URL. */
  case class Preview(key: String, value: String)
  val preview: Var[Option[Preview]]  = Var(None)

  def setPreview(p: Option[Preview]): Unit =
    if preview.now() != p then
      preview.set(p)
      bump()

  private def withPreview(key: String, vs: List[String]): List[String] =
    preview.now() match
      case Some(Preview(`key`, v)) if !vs.contains(v) => vs :+ v
      case _                                        => vs

  private var seq: Int  = 0
  private var high: Int = 0

  def currentSeq: Int = seq
  def maxSeq: Int     = high

  private var defaults: Map[String, String] = Map.empty

  private sealed trait FieldKind
  private case object Str  extends FieldKind
  private case object Num  extends FieldKind
  private case object Arr  extends FieldKind
  private case object Bool extends FieldKind

  private case class Field(
      key: String,
      param: String,
      hashKey: String,
      kind: FieldKind,
      label: Option[String],
      span: Boolean,
      api: Boolean,
      nochip: Boolean,
      readVar: () => Any,
      writeVar: String => Unit,
      writeArr: List[String] => Unit,
      writeBool: Boolean => Unit,
      clear: () => Unit
  )

  private def strField(key: String, param: String, v: Var[Option[String]],
      label: Option[String] = None, span: Boolean = false, api: Boolean = true,
      nochip: Boolean = false, hashKey: Option[String] = None): Field =
    Field(key, param, hashKey.getOrElse(param), Str, label, span, api, nochip,
      () => v.now(), s => v.set(if s.isEmpty then None else Some(s)),
      _ => (), _ => (), () => v.set(None))

  private def numField(key: String, param: String, v: Var[Option[Long]],
      label: Option[String] = None, span: Boolean = false): Field =
    Field(key, param, param, Num, label, span, api = true, nochip = false,
      () => v.now(), s => v.set(s.toLongOption),
      _ => (), _ => (), () => v.set(None))

  private def arrField(key: String, param: String, v: Var[List[String]],
      label: Option[String] = None, hashKey: Option[String] = None): Field =
    Field(key, param, hashKey.getOrElse(param), Arr, label, span = false, api = true, nochip = false,
      () => v.now(), _ => (), vs => v.set(vs), _ => (), () => v.set(Nil))

  private def boolField(key: String, param: String, v: Var[Boolean],
      span: Boolean = false, api: Boolean = true): Field =
    Field(key, param, param, Bool, None, span, api, nochip = false,
      () => v.now(), _ => (), _ => (), b => v.set(b), () => v.set(false))

  private val schema: List[Field] = List(
    strField("q", "q", q, Some("search")),
    arrField("tags", "tag", tags, Some("tag")),
    arrField("authors", "author", authors, Some("author")),
    arrField("places", "place", places, Some("place")),
    arrField("placeIds", "place", placeIds, Some("place"), hashKey = Some("placeId")),
    arrField("topics", "topic", topics, Some("topic")),
    arrField("status", "status", status, Some("status")),
    numField("yearFrom", "yearFrom", yearFrom, Some("published from")),
    numField("yearTo", "yearTo", yearTo, Some("published to")),
    numField("from", "from", from, Some("from"), span = true),
    numField("to", "to", to, Some("to"), span = true),
    strField("bbox", "bbox", bbox, span = true, api = false),
    boolField("inView", "inView", inView, span = true, api = false),
    strField("sort", "sort", sort, Some("sort"), nochip = true),
    strField("open", "open", open, span = true, api = false),
    strField("tab", "tab", tab, span = true, api = false),
    strField("aq", "aq", aq, span = true, api = false)
  )

  private val filters: List[Field] = schema.filterNot(_.span)

  def toFilter(except: Set[String] = Set.empty): Filter =
    val f = Filter.of(
      q = if except("q") then None else q.now(),
      tags = if except("tags") || except("tag") then Nil else withPreview("tags", tags.now()),
      authors = if except("authors") || except("author") then Nil else withPreview("authors", authors.now()),
      places =
        val ps = if except("places") || except("place") then Nil else withPreview("places", places.now())
        val pi = if except("placeIds") || except("place") then Nil else placeIds.now()
        ps ++ pi,
      topics = if except("topics") || except("topic") then Nil else withPreview("topics", topics.now()),
      status = if except("status") then Nil else withPreview("status", status.now()),
      yearFrom = if except("yearFrom") then None else yearFrom.now(),
      yearTo = if except("yearTo") then None else yearTo.now(),
      from = if except("from") then None else from.now(),
      to = if except("to") then None else to.now(),
      minLon =
        if except("bbox") || !inView.now() then None
        else bbox.now().flatMap(parseBbox).map(_._1),
      minLat =
        if except("bbox") || !inView.now() then None
        else bbox.now().flatMap(parseBbox).map(_._2),
      maxLon =
        if except("bbox") || !inView.now() then None
        else bbox.now().flatMap(parseBbox).map(_._3),
      maxLat =
        if except("bbox") || !inView.now() then None
        else bbox.now().flatMap(parseBbox).map(_._4),
      sort = sort.now(),
      limit = None,
      offset = None
    )
    val p = f.period
    val corrected =
      if p.exists(pp => pp.start > pp.end) then f.copy(period = p.map(pp => Period(pp.end, pp.start)))
      else f
    val y = corrected.years
    if y.exists(yy => yy.start > yy.end) then corrected.copy(years = y.map(yy => Period(yy.end, yy.start)))
    else corrected

  private def parseBbox(s: String): Option[(Double, Double, Double, Double)] =
    val parts = s.split(",").flatMap(_.trim.toDoubleOption)
    if parts.length == 4 then Some((parts(0), parts(1), parts(2), parts(3)))
    else None

  def clearFilters(): Unit =
    filters.foreach(_.clear())
    writeHash(push = false)

  case class ActiveChip(key: String, label: String, value: String, shown: Option[String] = None)

  def activeChips(): List[ActiveChip] =
    filters.filterNot(_.nochip).flatMap { f =>
      f.kind match
        case Arr =>
          f.readVar().asInstanceOf[List[String]].map(v =>
            ActiveChip(f.key, f.label.getOrElse(f.key), v))
        case Str =>
          f.readVar().asInstanceOf[Option[String]].toList.map(v =>
            ActiveChip(f.key, f.label.getOrElse(f.key), v))
        case Num =>
          f.readVar().asInstanceOf[Option[Long]].toList.map(v =>
            ActiveChip(f.key, f.label.getOrElse(f.key), v.toString))
        case Bool =>
          if f.readVar().asInstanceOf[Boolean] then
            List(ActiveChip(f.key, f.label.getOrElse(f.key), "true"))
          else Nil
    }

  def removeChip(key: String, value: String): Unit =
    schema.find(_.key == key).foreach { f =>
      f.kind match
        case Arr => f.writeArr(f.readVar().asInstanceOf[List[String]].filterNot(_ == value))
        case _   => f.clear()
    }
    writeHash(push = false)

  private val changeSignal: Var[Int] = Var(0)
  def signal: Signal[Int] = changeSignal.signal

  private def bump(): Unit = changeSignal.update(_ + 1)

  def set(patches: (String, Any)*)(push: Boolean = false): Unit =
    for (k, v) <- patches do
      schema.find(_.key == k).foreach { f =>
        (f.kind, v) match
          case (Str, s: String) => f.writeVar(s)
          case (Str, Some(s: String)) => f.writeVar(s)
          case (Str, None) => f.clear()
          case (Num, n: Long) => f.writeVar(n.toString)
          case (Num, Some(n: Long)) => f.writeVar(n.toString)
          case (Num, Some(n: Int)) => f.writeVar(n.toString)
          case (Num, n: Int) => f.writeVar(n.toString)
          case (Num, None) => f.clear()
          case (Arr, xs: List[?]) => f.writeArr(xs.map(_.toString))
          case (Bool, b: Boolean) => f.writeBool(b)
          case _ => ()
      }
    writeHash(push)
    bump()

  private def readHash(): Map[String, Any] =
    val hash = window.location.hash.stripPrefix("#")
    if hash.isEmpty then return Map.empty
    val params = new dom.URLSearchParams(hash)
    val result = scala.collection.mutable.Map.empty[String, Any]
    for f <- schema do
      f.kind match
        case Arr =>
          val values = scala.collection.mutable.ListBuffer.empty[String]
          params.forEach { (value, key) =>
            if key == f.hashKey then values += value
          }
          if values.nonEmpty then result(f.key) = values.toList
        case Str =>
          val v = params.get(f.hashKey)
          if v != null && v.nonEmpty then result(f.key) = v
        case Num =>
          val v = params.get(f.hashKey)
          if v != null then v.toLongOption.foreach(n => result(f.key) = n)
        case Bool =>
          val v = params.get(f.hashKey)
          if v == "true" then result(f.key) = true
    result.toMap

  private def writeHash(push: Boolean): Unit =
    val params = new dom.URLSearchParams("")
    for f <- schema do
      f.kind match
        case Arr =>
          f.readVar().asInstanceOf[List[String]].foreach(v => params.append(f.hashKey, v))
        case Str =>
          f.readVar().asInstanceOf[Option[String]].foreach(v => params.set(f.hashKey, v))
        case Num =>
          f.readVar().asInstanceOf[Option[Long]].foreach(v => params.set(f.hashKey, v.toString))
        case Bool =>
          if f.readVar().asInstanceOf[Boolean] then params.set(f.hashKey, "true")
    val qs = params.toString
    val url = if qs.nonEmpty then "#" + qs else window.location.pathname + window.location.search
    if push then
      seq = { high += 1; high }
      window.history.pushState(js.Dynamic.literal(histmersionSeq = seq), "", url)
    else
      window.history.replaceState(js.Dynamic.literal(histmersionSeq = seq), "", url)

  private def applyMap(m: Map[String, Any]): Unit =
    for (k, v) <- m do
      schema.find(_.key == k).foreach { f =>
        (f.kind, v) match
          case (Arr, xs: List[?])     => f.writeArr(xs.map(_.toString))
          case (Str, s: String)       => f.writeVar(s)
          case (Num, n: Long)         => f.writeVar(n.toString)
          case (Num, n: Int)          => f.writeVar(n.toString)
          case (Bool, b: Boolean)     => f.writeBool(b)
          case _                      => ()
      }

  def init(config: UiConfig): Unit =
    defaults = Map(
      "from" -> config.time.defaultFrom.toString,
      "to"   -> config.time.defaultTo.toString
    )
    val base = Map[String, Any](
      "from" -> config.time.defaultFrom,
      "to"   -> config.time.defaultTo
    )
    applyMap(base ++ readHash())
    window.addEventListener("popstate", { (e: dom.PopStateEvent) =>
      val state = e.state
      seq = if state != null then
        Try(state.asInstanceOf[js.Dynamic].histmersionSeq.asInstanceOf[Int]).getOrElse(0)
      else 0
      schema.foreach(_.clear())
      applyMap(base ++ readHash())
      bump()
    })

  def addHash(hash: String): Unit =
    val h = new dom.URLSearchParams(hash.stripPrefix("#"))
    val patches = scala.collection.mutable.ListBuffer.empty[(String, Any)]
    for f <- schema do
      val key = f.hashKey
      val has = { var found = false; h.forEach((_, k) => if k == key then found = true); found }
      if has then
        f.kind match
          case Arr =>
            val current = f.readVar().asInstanceOf[List[String]]
            val newVals = scala.collection.mutable.ListBuffer.empty[String]
            h.forEach { (v, k) => if k == key && v.nonEmpty && !current.contains(v) then newVals += v }
            if newVals.nonEmpty then patches += f.key -> (current ++ newVals.toList)
          case _ =>
            val v = h.get(key)
            if v != null then patches += f.key -> v
    if patches.nonEmpty then set(patches.toList*)(push = false)
