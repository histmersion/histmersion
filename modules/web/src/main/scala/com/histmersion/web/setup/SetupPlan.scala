package com.histmersion.web.setup

import com.histmersion.api.MapArea

/** The settings and the downloads a setup's choices come to. Pure. */
object SetupPlan:

  /** An e-mail address that can be one (empty: none, which is allowed). */
  def email(s: String): Either[String, String] =
    val t = s.trim
    if t.isEmpty || t.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+") then Right(t)
    else Left(s"$t does not look like an e-mail address.")

  /** The settings to write: what differs from `before`, and the mark that the setup was done. A change the file already
    * holds is skipped by the server, so writing it again does no harm.
    */
  def changes(c: SetupChoices, before: SetupChoices): List[(String, Option[String])] =
    val finders = c.finders.toList.sortBy(_._1).collect {
      case (id, on) if !before.finders.get(id).contains(on) => s"plugins.$id.enabled" -> Some(on.toString)
    }
    val start = if c.start == before.start then Nil
    else com.histmersion.web.settings.StartPlace.changes(c.start).map((k, v) => k -> Some(v))
    List(
      Option.when(c.email.trim != before.email.trim)("acquire.contact-email" -> Some(c.email.trim)),
      c.area.filter(a => !before.area.contains(a)).map(a => "basemap.bbox" -> Some(a)),
      Option.when(c.agents != before.agents)("agents.enabled" -> Some(c.agents.toString)),
      Option.when(c.calibre.trim != before.calibre.trim && c.calibre.trim.nonEmpty)(
        "cli.calibre.library-dir" -> Some(c.calibre.trim)
      )
    ).flatten ++ finders ++ start :+ ("setup.completed" -> Some("true"))

  def downloads(c: SetupChoices): List[SetupDownload] =
    c.area.map(SetupDownload.Map(_)).toList ++
      Option.when(c.boundaries)(SetupDownload.Boundaries) ++
      Option.when(c.events)(SetupDownload.Events) ++
      Option.when(c.pictures)(SetupDownload.Pictures) ++
      Option.when(c.morePlaces)(SetupDownload.MorePlaces)

  /** About how much the chosen map area downloads, in GB. */
  def mapGb(c: SetupChoices, areas: List[MapArea]): Double =
    c.area.flatMap(a => areas.find(_.bbox == a)).fold(0.0)(_.approxGb)
