package com.histmersion.api.media
import com.histmersion.api.given
object AssetsMarkdown:

  type AssetsResult = AssetsResult.T
  object AssetsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type AssetsCitekey = AssetsCitekey.T
  object AssetsCitekey extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def assets(citekey: com.histmersion.api.media.AssetsMarkdown.AssetsCitekey, as: Vector[AssetInfo]): com.histmersion.api.media.AssetsMarkdown.AssetsResult =
    if as.isEmpty then _root_.com.histmersion.api.media.AssetsMarkdown.AssetsResult(s"_No assets stored for **$citekey**._")
    else
      val rows = as.map { a =>
        val size = (a.width, a.height) match
          case (Some(w), Some(h)) => s"$w×$h"
          case _                  => "—"
        s"| ${a.name} | ${a.mime} | $size | ${a.bytes} | `${AssetLinks.url(citekey, a.name)}` |"
      }
      _root_.com.histmersion.api.media.AssetsMarkdown.AssetsResult((List(
        s"### ${as.size} asset(s) · $citekey",
        "",
        "| name | mime | size | bytes | url |",
        "|---|---|---|---:|---|"
      ) ++ rows).mkString("\n"))
