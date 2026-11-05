package com.histmersion.plugins.fetchers

import hearth.kindlings.sconfigderivation.ConfigReader

/** `oa-unpaywall`'s contact, as its block has it in effect (`null`: none of its own). */
final case class UnpaywallPeer(contactEmail: Option[UnpaywallPeer.ContactEmail] = None) derives ConfigReader

object UnpaywallPeer:
  type ContactEmail = ContactEmail.T
  object ContactEmail extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
