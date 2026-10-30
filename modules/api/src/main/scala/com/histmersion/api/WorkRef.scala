package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject
import hearth.kindlings.tapirschemaderivation.KindlingsSchema
import sttp.tapir.Schema

final case class WorkRef(
    doi: Option[String] = None,
    openalexId: Option[String] = None,
    arxivId: Option[String] = None,
    title: Option[String] = None,
    year: Option[Int] = None,
    authors: List[String] = Nil,
    venue: Option[String] = None,
    citedByCount: Option[Int] = None,
    isOa: Boolean = false,
    oaStatus: Option[String] = None,
    oaUrl: Option[String] = None,
    isRetracted: Boolean = false,
    workType: Option[String] = None,
    abstractText: Option[String] = None,
    referencedWorks: List[String] = Nil,
    citekey: Option[String] = None,
    inDb: Boolean = false,
    oaLocations: List[String] = Nil,
    oaLicence: Option[String] = None
) derives KindlingsCodecAsObject,
      KindlingsSchema
object WorkRef:
  given Schema[WorkRef] = summon[KindlingsSchema[WorkRef]].schema
