package com.histmersion.api

/** A source's figures as Markdown: the `assets` tool's answer and the CLI's listing. */
object AssetsMarkdown:

  def assets(citekey: String, as: Vector[AssetInfo]): String =
    if as.isEmpty then s"_No assets stored for **$citekey**._"
    else
      val rows = as.map { a =>
        val size = (a.width, a.height) match
          case (Some(w), Some(h)) => s"$w×$h"
          case _                  => "—"
        s"| ${a.name} | ${a.mime} | $size | ${a.bytes} | `${AssetLinks.url(citekey, a.name)}` |"
      }
      (List(
        s"### ${as.size} asset(s) · $citekey",
        "",
        "| name | mime | size | bytes | url |",
        "|---|---|---|---:|---|"
      ) ++ rows).mkString("\n")
