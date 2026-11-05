package com.histmersion.web.setup

import com.histmersion.api.catalogue.MapArea

/** The settings and the downloads a setup's choices come to. Pure. */
object SetupPlan:

  /** An e-mail address that can be one (empty: none, which is allowed). */
  type EmailResultPart1 = EmailResultPart1.T
  object EmailResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EmailResultPart2 = EmailResultPart2.T
  object EmailResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type EmailS = EmailS.T
  object EmailS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChangesResultPart1 = ChangesResultPart1.T
  object ChangesResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChangesResultPart2 = ChangesResultPart2.T
  object ChangesResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MapGbResult = MapGbResult.T
  object MapGbResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Double, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def email(s: com.histmersion.web.setup.SetupPlan.EmailS): Either[com.histmersion.web.setup.SetupPlan.EmailResultPart1, com.histmersion.web.setup.SetupPlan.EmailResultPart2] =
    val t = s.trim
    if t.isEmpty || t.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+") then Right(t)
    else Left(s"$t does not look like an e-mail address.")

  /** The settings to write: what differs from `before`, and the mark that the setup was done. A change the file already
    * holds is skipped by the server, so writing it again does no harm.
    */
  def changes(c: SetupChoices, before: SetupChoices): List[(com.histmersion.web.setup.SetupPlan.ChangesResultPart1, Option[com.histmersion.web.setup.SetupPlan.ChangesResultPart2])] =
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
  def mapGb(c: SetupChoices, areas: List[MapArea]): com.histmersion.web.setup.SetupPlan.MapGbResult =
    c.area.flatMap(a => areas.find(_.bbox == a)).fold(0.0)(_.approxGb)
