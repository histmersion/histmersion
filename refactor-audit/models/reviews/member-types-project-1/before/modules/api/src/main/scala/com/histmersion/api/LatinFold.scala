package com.histmersion.api

/** Diacritic folding as a lookup table: this code is also compiled to Scala.js, which has no `java.text.Normalizer`.
  * The single-letter part was generated from Unicode NFD decompositions; the letters NFD does not decompose (`ß`, `æ`,
  * `ø`, `ł`, `đ`, `þ`, …) are mapped by hand.
  */
object LatinFold:

  private val Pairs: String =
    // Latin-1 Supplement
    "ÀAÁAÂAÃAÄAÅAÇCÈEÉEÊEËEÌIÍIÎIÏIÐDÑNÒOÓOÔOÕOÖOØOÙUÚUÛUÜUÝYàaáaâaãaäaåaçcèeéeêeëeìi" +
      "íiîiïiðdñnòoóoôoõoöoøoùuúuûuüuýyÿy" +
      // Latin Extended-A
      "ĀAāaĂAăaĄAąaĆCćcĈCĉcĊCċcČCčcĎDďdĐDđdĒEēeĔEĕeĖEėeĘEęeĚEěeĜGĝgĞGğgĠGġgĢGģgĤHĥhĦHħh" +
      "ĨIĩiĪIīiĬIĭiĮIįiİIıiĴJĵjĶKķkĸqĹLĺlĻLļlĽLľlĿLŀlŁLłlŃNńnŅNņnŇNňnŉnŊNŋnŌOōoŎOŏoŐOőo" +
      "ŔRŕrŖRŗrŘRřrŚSśsŜSŝsŞSşsŠSšsŢTţtŤTťtŦTŧtŨUũuŪUūuŬUŭuŮUůuŰUűuŲUųuŴWŵwŶYŷyŸYŹZźzŻZ" +
      "żzŽZžzſs" +
      // Latin Extended-B
      "ƀbƁBƇCƈcƉDƊDƑFƒfƗIƘKƙkƚlƝNƞnƟOƠOơoƤPƥpƫtƬTƭtƮTƯUưuƲVƳYƴyƵZƶzǍAǎaǏIǐiǑOǒoǓUǔuǕUǖu" +
      "ǗUǘuǙUǚuǛUǜuǝeǞAǟaǠAǡaǤGǥgǦGǧgǨKǩkǪOǫoǬOǭoǰjǴGǵgǸNǹnǺAǻaǾOǿoȀAȁaȂAȃaȄEȅeȆEȇeȈIȉi" +
      "ȊIȋiȌOȍoȎOȏoȐRȑrȒRȓrȔUȕuȖUȗuȘSșsȚTțtȞHȟhȡdȤZȥzȦAȧaȨEȩeȪOȫoȬOȭoȮOȯoȰOȱoȲYȳyȴlȵnȶt" +
      "ȷjȺAȻCȼcȽLȾTȿsɀzɃBɄUɆEɇeɈJɉjɌRɍrɎYɏy" +
      // Latin Extended Additional
      "ḀAḁaḂBḃbḄBḅbḆBḇbḈCḉcḊDḋdḌDḍdḎDḏdḐDḑdḒDḓdḔEḕeḖEḗeḘEḙeḚEḛeḜEḝeḞFḟfḠGḡgḢHḣhḤHḥhḦHḧh" +
      "ḨHḩhḪHḫhḬIḭiḮIḯiḰKḱkḲKḳkḴKḵkḶLḷlḸLḹlḺLḻlḼLḽlḾMḿmṀMṁmṂMṃmṄNṅnṆNṇnṈNṉnṊNṋnṌOṍoṎOṏo" +
      "ṐOṑoṒOṓoṔPṕpṖPṗpṘRṙrṚRṛrṜRṝrṞRṟrṠSṡsṢSṣsṤSṥsṦSṧsṨSṩsṪTṫtṬTṭtṮTṯtṰTṱtṲUṳuṴUṵuṶUṷu" +
      "ṸUṹuṺUṻuṼVṽvṾVṿvẀWẁwẂWẃwẄWẅwẆWẇwẈWẉwẊXẋxẌXẍxẎYẏyẐZẑzẒZẓzẔZẕzẖhẗtẘwẙyẜsẝsẠAạaẢAảa" +
      "ẤAấaẦAầaẨAẩaẪAẫaẬAậaẮAắaẰAằaẲAẳaẴAẵaẶAặaẸEẹeẺEẻeẼEẽeẾEếeỀEềeỂEểeỄEễeỆEệeỈIỉiỊIịi" +
      "ỌOọoỎOỏoỐOốoỒOồoỔOổoỖOỗoỘOộoỚOớoỜOờoỞOởoỠOỡoỢOợoỤUụuỦUủuỨUứuỪUừuỬUửuỮUữuỰUựuỲYỳy" +
      "ỴYỵyỶYỷyỸYỹy"

  /** The transliteration half rings (`ʿ` ayin, `ʾ` aleph) fold to nothing: `Baʿal` → `Baal`. */
  private val Multi: Map[Char, String] = Map(
    '\u02bf' -> "",
    '\u02be' -> "",
    'Æ' -> "AE",
    'Þ' -> "TH",
    'ß' -> "ss",
    'æ' -> "ae",
    'þ' -> "th",
    'Ĳ' -> "IJ",
    'ĳ' -> "ij",
    'Œ' -> "OE",
    'œ' -> "oe",
    'Ǆ' -> "DZ",
    'ǅ' -> "Dz",
    'ǆ' -> "dz",
    'Ǉ' -> "LJ",
    'ǈ' -> "Lj",
    'ǉ' -> "lj",
    'Ǌ' -> "NJ",
    'ǋ' -> "Nj",
    'ǌ' -> "nj",
    'Ǣ' -> "AE",
    'ǣ' -> "ae",
    'Ǳ' -> "DZ",
    'ǲ' -> "Dz",
    'ǳ' -> "dz",
    'Ǽ' -> "AE",
    'ǽ' -> "ae",
    'ẞ' -> "SS",
    'Ỻ' -> "LL",
    'ỻ' -> "ll"
  )

  private val Table: Map[Char, String] =
    Pairs.grouped(2).map(p => p(0) -> p(1).toString).toMap ++ Multi

  def tableChars: Set[Char] = Table.keySet

  private def isCombiningMark(c: Char): Boolean = c >= '\u0300' && c <= '\u036f'

  def foldChar(c: Char): String =
    if isCombiningMark(c) then "" else Table.getOrElse(c, c.toString)

  def fold(s: String): String =
    val b = new StringBuilder(s.length)
    s.foreach { c =>
      if !isCombiningMark(c) then
        Table.get(c) match
          case Some(r) => b ++= r
          case None    => b += c
    }
    b.result()
