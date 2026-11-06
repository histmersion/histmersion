package com.histmersion.api.media
import com.histmersion.api.given
object AssetsMarkdown:


  def assets(citekey: com.histmersion.api.library.Citekey, as: Vector[AssetInfo]): com.histmersion.api.Markdown.Text =
    if as.isEmpty then _root_.com.histmersion.api.Markdown.Text(s"_No assets stored for **$citekey**._")
    else
      val rows = as.map { a =>
        val size = (a.width, a.height) match
          case (Some(w), Some(h)) => s"$w×$h"
          case _                  => "—"
        s"| ${a.name} | ${a.mime} | $size | ${a.bytes} | `${AssetLinks.url(citekey, a.name)}` |"
      }
      _root_.com.histmersion.api.Markdown.Text((List(
        s"### ${as.size} asset(s) · $citekey",
        "",
        "| name | mime | size | bytes | url |",
        "|---|---|---|---:|---|"
      ) ++ rows).mkString("\n"))
