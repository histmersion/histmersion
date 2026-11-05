package com.histmersion.domain.media.covers

/** Where a book's cover comes from (#1446), in order: the cover the local Calibre library keeps for the entry (the
  * user's own copy's cover — it is the edition they hold), else Open Library's by the first usable ISBN (the record's,
  * then Calibre's), else by OCLC number; else none. Pure: whether the Calibre file exists is a parameter.
  */
object CoverChoice:
  enum Pick:
    case Calibre(path: String)
    case OpenLibrary(kind: String, id: String)

  def choose(
      calibreCover: Option[String],
      exists: String => Boolean,
      isbns: List[String],
      oclc: Option[String]
  ): Option[Pick] =
    calibreCover
      .map(_.trim)
      .filter(p => p.nonEmpty && exists(p))
      .map(Pick.Calibre(_))
      .orElse(isbns.flatMap(OpenLibrary.isbn).headOption.map(Pick.OpenLibrary("isbn", _)))
      .orElse(oclc.flatMap(OpenLibrary.oclc).map(Pick.OpenLibrary("oclc", _)))
