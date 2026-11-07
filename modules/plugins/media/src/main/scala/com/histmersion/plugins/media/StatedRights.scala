package com.histmersion.plugins.media

import com.histmersion.plugin.{Licence, MediaRights}

extension (r: MediaRights.type)
  /** A record its provider releases under CC0, as the provider's terms state it. */
  def cc0(author: Option[MediaRights.Author], credit: MediaRights.Credit): MediaRights =
    MediaRights(Licence("CC0-1.0"), Some(com.histmersion.plugin.MediaRights.LicenceUrl("https://creativecommons.org/publicdomain/zero/1.0/")), author, credit)

  /** A record its provider states is in the public domain; `url` names the statement. */
  def publicDomain(
      author: Option[MediaRights.Author],
      credit: MediaRights.Credit,
      url: Option[MediaRights.LicenceUrl] = Some(MediaRights.LicenceUrl("https://creativecommons.org/publicdomain/mark/1.0/"))
  ): MediaRights =
    MediaRights(Licence("public-domain"), url, author, credit)
