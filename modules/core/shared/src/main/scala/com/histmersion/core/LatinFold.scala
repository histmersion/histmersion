package com.histmersion.core

/** Diacritic folding for Latin script — `Güterbock` → `Guterbock`, `Ḫattuša` → `Hattusa`,
  * `Łukasz` → `Lukasz`, `Straße` → `Strasse` — as a pure lookup table, because the shared core is
  * also compiled to Scala.js, which has no `java.text.Normalizer` (#878).
  *
  * Covers Latin-1 Supplement, Latin Extended-A and -B and Latin Extended Additional (the blocks the
  * transliterations in the corpus use: `ḫ`, `ṣ`, `ṭ`, `ā`, `ś`, Vietnamese tone letters), plus the
  * combining marks U+0300–U+036F of input that arrives already decomposed. Case is kept; every other
  * character (other scripts, digits, punctuation) passes through unchanged. The single-letter part of
  * the table was generated from Unicode NFD decompositions; the letters NFD does not decompose
  * (`ß`, `æ`, `ø`, `ł`, `đ`, `þ`, …) are mapped by hand. */
object LatinFold:

  /** Pairs `<letter><base>` for every letter that folds to ONE ASCII letter. */
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

  /** The letters that fold to more than one ASCII letter, and the transliteration half rings
    * (`ʿ` ayin, `ʾ` aleph: `Baʿal` → `Baal`) that fold to nothing. */
  private val Multi: Map[Char, String] = Map(
    '\u02bf' -> "", '\u02be' -> "",
    'Æ' -> "AE", 'Þ' -> "TH", 'ß' -> "ss", 'æ' -> "ae", 'þ' -> "th", 'Ĳ' -> "IJ", 'ĳ' -> "ij",
    'Œ' -> "OE", 'œ' -> "oe", 'Ǆ' -> "DZ", 'ǅ' -> "Dz", 'ǆ' -> "dz", 'Ǉ' -> "LJ", 'ǈ' -> "Lj",
    'ǉ' -> "lj", 'Ǌ' -> "NJ", 'ǋ' -> "Nj", 'ǌ' -> "nj", 'Ǣ' -> "AE", 'ǣ' -> "ae", 'Ǳ' -> "DZ",
    'ǲ' -> "Dz", 'ǳ' -> "dz", 'Ǽ' -> "AE", 'ǽ' -> "ae", 'ẞ' -> "SS", 'Ỻ' -> "LL", 'ỻ' -> "ll")

  private val Table: Map[Char, String] =
    Pairs.grouped(2).map(p => p(0) -> p(1).toString).toMap ++ Multi

  private def isCombiningMark(c: Char): Boolean = c >= '\u0300' && c <= '\u036f'

  /** One character folded: its ASCII letter(s), `""` for a combining mark or a half ring, or the
    * character itself. `fold(s)` is this over every character of `s`. */
  def foldChar(c: Char): String =
    if isCombiningMark(c) then "" else Table.getOrElse(c, c.toString)

  /** `s` with every Latin diacritic removed, case kept. */
  def fold(s: String): String =
    val b = new StringBuilder(s.length) // local accumulator
    s.foreach { c =>
      if !isCombiningMark(c) then
        Table.get(c) match
          case Some(r) => b ++= r
          case None    => b += c
    }
    b.result()
