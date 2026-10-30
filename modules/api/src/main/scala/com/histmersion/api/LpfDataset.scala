package com.histmersion.api

/** Read from a FeatureCollection's own keys or a sidecar `<file>.meta.json`: an LP-TSV file has nowhere else to say it.
  */
final case class LpfDataset(
    title: Option[String],
    citation: Option[String],
    licence: Option[String],
    span: Span = Span.always
)
