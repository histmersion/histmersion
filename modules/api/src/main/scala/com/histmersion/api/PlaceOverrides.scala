package com.histmersion.api

import com.histmersion.values.PlaceKind

object PlaceOverrides:
  val MaxReason = 500

  private def invalid(msg: String) = ApiError(msg, Some(Write.CodeInvalid))

  def edit(in: PlaceOverrideSet): Either[ApiError, PlaceOverrideEdit] =
    val kind = in.kind.map(_.trim.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty)
    val reason = in.reason.map(_.trim.replaceAll("\\s+", " ")).filter(_.nonEmpty)
    val period = Option.when(in.periodStart.isDefined || in.periodEnd.isDefined)((in.periodStart, in.periodEnd))
    val asked = in.pointLat.isDefined || in.pointLon.isDefined
    if in.reset then
      if kind.isDefined || period.isDefined || asked then
        Left(invalid("`reset` drops the override — pass no kind, period or point with it"))
      else Right(PlaceOverrideEdit.Reset)
    else
      for
        _ <- Either.cond(
          kind.isDefined || period.isDefined || asked,
          (),
          invalid("say what to set: a kind, a period (periodStart/periodEnd), a point (pointLat/pointLon), or reset")
        )
        point <-
          if !asked then Right(None)
          else
            (in.pointLat, in.pointLon) match
              case (Some(lat), Some(lon)) =>
                if lat.isNaN || lon.isNaN || lat < -90 || lat > 90 || lon < -180 || lon > 180 then
                  Left(invalid(s"($lat, $lon) is not a latitude/longitude"))
                else if reason.isEmpty then
                  Left(
                    invalid(
                      "say where the point comes from (`reason`): a gazetteer value such as a Pleiades " +
                        "representative point or a Wikidata coordinate — never an estimate"
                    )
                  )
                else Right(Some(GeoPoint(lon, lat)))
              case _ => Left(invalid("a point needs both pointLat and pointLon"))
        _ <- kind.fold(Right(()))(k =>
          Either.cond(
            PlaceKind.all.contains(k),
            (),
            invalid(s"kind '$k' is not one of ${PlaceKind.all.mkString(", ")}")
          )
        )
        _ <- period match
          case Some((Some(a), Some(b))) if a > b => Left(invalid(s"the period starts ($a) after it ends ($b)"))
          case _                                 => Right(())
        _ <- Either.cond(
          reason.forall(_.length <= MaxReason),
          (),
          invalid(s"a reason is at most $MaxReason characters")
        )
      yield PlaceOverrideEdit.Put(kind, period, reason, point)

  def reason(raw: Option[String]): Either[ApiError, Option[String]] =
    val r = raw.map(_.trim.replaceAll("\\s+", " ")).filter(_.nonEmpty)
    Either.cond(r.forall(_.length <= MaxReason), r, invalid(s"a reason is at most $MaxReason characters"))

  def parsePoint(raw: String): Either[String, (Double, Double)] =
    raw.split(",", -1).map(_.trim).toList match
      case List(a, b) =>
        (a.toDoubleOption, b.toDoubleOption) match
          case (Some(lat), Some(lon))
              if !lat.isNaN && !lon.isNaN && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180 =>
            Right((lat, lon))
          case (Some(_), Some(_)) => Left(s"'$raw' is not a latitude,longitude (latitude −90..90, longitude −180..180)")
          case _                  => Left(s"'$raw' is not a latitude,longitude pair of numbers, e.g. 37.95,58.21")
      case _ => Left(s"'$raw' is not latitude,longitude, e.g. 37.95,58.21")

  def pointText(lat: Double, lon: Double): String =
    String.format(
      java.util.Locale.ROOT,
      "%.4f %s %.4f %s",
      math.abs(lat),
      if lat < 0 then "S" else "N",
      math.abs(lon),
      if lon < 0 then "W" else "E"
    )

  def periodText(start: Option[Long], end: Option[Long]): String = YearFmt.openPeriod(start, end)
