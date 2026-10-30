package com.histmersion.domain.personal

import com.histmersion.api.personal.Target

/** A target as the tables' columns hold it: plain values, which the database driver binds. */
final private[personal] case class TargetColumns(
    kind: String,
    key: Option[String],
    lat: Option[Double],
    lon: Option[Double],
    zoom: Option[Double],
    from: Option[Long],
    to: Option[Long],
    view: Option[String]
):
  def user(u: UserId): String = u.value

private[personal] object TargetColumns:
  def of(t: Target): TargetColumns =
    TargetColumns(
      t.kind.wire,
      t.itemKey,
      t.lat.map(v => v.value: Double),
      t.lon.map(v => v.value: Double),
      t.zoom.map(v => v.value: Double),
      t.from.map(v => (v.value: Int).toLong),
      t.to.map(v => (v.value: Int).toLong),
      t.view.map(v => v.value: String)
    )
