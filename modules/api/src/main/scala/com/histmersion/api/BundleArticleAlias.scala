package com.histmersion.api

import hearth.kindlings.circederivation.KindlingsCodecAsObject

import java.util.UUID

/** An old slug of an article (`article_aliases`, V042) and the article it names now. */
final case class BundleArticleAlias(alias: String, articleUid: UUID, createdAt: String) derives KindlingsCodecAsObject
