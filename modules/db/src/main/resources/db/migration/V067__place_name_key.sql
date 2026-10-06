-- V067: the place-name KEY the geocoder compares (#981).
--
-- `enrich-source` turns a model's place NAME into a gazetteer point. It used to compare
-- `lower(name)` only, so "Kraków" never met "Krakow", "Ur" never met Pleiades' "Ur(i)" and
-- "Kievan Rus" never met "Kievan Rus'". The key is the name as a person would compare it:
--
--   1. parenthesised asides dropped        "Ur(i)" → "Ur", "Levant (region)" → "Levant"
--   2. Latin diacritics folded             the LatinFold table (core): "Kraków" → "Krakow", "Ḫattuša" → "Hattusa"
--   3. apostrophes and combining marks dropped   "Kievan Rus'" → "Kievan Rus"
--   4. ASCII lower case                    only A–Z, so the key does not depend on the database's locale
--   5. punctuation and spaces → one space  "Tell el-Muqayyar" → "tell el muqayyar"
--   6. a leading "the " dropped            "the Levant" → "levant"
--
-- The function body is GENERATED from `LatinFold` and must equal `Geocode.key` (core) character for
-- character: `GeocoderSuite` runs every character of the table, and the separators, through both.
-- Change one, regenerate the other in a NEW migration. Non-Latin scripts pass through unchanged.

CREATE OR REPLACE FUNCTION histmersion_place_key(t text) RETURNS text
LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE AS $fn$
SELECT
regexp_replace(btrim(regexp_replace(translate(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(replace(regexp_replace(t, '\([^)]*\)', ' ', 'g'), 'Æ', 'ae'), 'Þ', 'th'), 'ß', 'ss'), 'æ', 'ae'), 'þ', 'th'), 'Ĳ', 'ij'), 'ĳ', 'ij'), 'Œ', 'oe'), 'œ', 'oe'), 'Ǆ', 'dz'), 'ǅ', 'dz'), 'ǆ', 'dz'), 'Ǉ', 'lj'), 'ǈ', 'lj'), 'ǉ', 'lj'), 'Ǌ', 'nj'), 'ǋ', 'nj'), 'ǌ', 'nj'), 'Ǣ', 'ae'), 'ǣ', 'ae'), 'Ǳ', 'dz'), 'ǲ', 'dz'), 'ǳ', 'dz'), 'Ǽ', 'ae'), 'ǽ', 'ae'), 'ẞ', 'ss'), 'Ỻ', 'll'), 'ỻ', 'll'),
      'ÀÁÂÃÄÅÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖØÙÚÛÜÝàáâãäåçèéêëìíîïðñòóôõöøùúûüýÿĀāĂăĄąĆćĈĉĊċČčĎďĐđĒēĔĕĖėĘęĚěĜĝĞğĠġĢģĤĥĦħĨĩĪīĬĭĮįİıĴĵĶķĸĹĺĻļĽľĿŀŁłŃńŅņŇňŉŊŋŌōŎŏŐőŔŕŖŗŘřŚśŜŝŞşŠšŢţŤťŦŧŨũŪūŬŭŮůŰűŲųŴŵŶŷŸŹźŻżŽžſƀƁƇƈƉƊƑƒƗƘƙƚƝƞƟƠơƤƥƫƬƭƮƯưƲƳƴƵƶǍǎǏǐǑǒǓǔǕǖǗǘǙǚǛǜǝǞǟǠǡǤǥǦǧǨǩǪǫǬǭǰǴǵǸǹǺǻǾǿȀȁȂȃȄȅȆȇȈȉȊȋȌȍȎȏȐȑȒȓȔȕȖȗȘșȚțȞȟȡȤȥȦȧȨȩȪȫȬȭȮȯȰȱȲȳȴȵȶȷȺȻȼȽȾȿɀɃɄɆɇɈɉɌɍɎɏḀḁḂḃḄḅḆḇḈḉḊḋḌḍḎḏḐḑḒḓḔḕḖḗḘḙḚḛḜḝḞḟḠḡḢḣḤḥḦḧḨḩḪḫḬḭḮḯḰḱḲḳḴḵḶḷḸḹḺḻḼḽḾḿṀṁṂṃṄṅṆṇṈṉṊṋṌṍṎṏṐṑṒṓṔṕṖṗṘṙṚṛṜṝṞṟṠṡṢṣṤṥṦṧṨṩṪṫṬṭṮṯṰṱṲṳṴṵṶṷṸṹṺṻṼṽṾṿẀẁẂẃẄẅẆẇẈẉẊẋẌẍẎẏẐẑẒẓẔẕẖẗẘẙẜẝẠạẢảẤấẦầẨẩẪẫẬậẮắẰằẲẳẴẵẶặẸẹẺẻẼẽẾếỀềỂểỄễỆệỈỉỊịỌọỎỏỐốỒồỔổỖỗỘộỚớỜờỞởỠỡỢợỤụỦủỨứỪừỬửỮữỰựỲỳỴỵỶỷỸỹABCDEFGHIJKLMNOPQRSTUVWXYZ  !"#$%&()*+,-./:;<=>?@[\]^_{|}~‐‑‒–—―“”„«»·…' || chr(9) || chr(10) || chr(13) || '̴̵̶̷̸̡̢̧̨̛̖̗̘̙̜̝̞̟̠̣̤̥̦̩̪̫̬̭̮̯̰̱̲̳̹̺̻̼͇͈͉͍͎̀́̂̃̄̅̆̇̈̉̊̋̌̍̎̏̐̑̒̓̔̽̾̿̀́͂̓̈́͆͊͋͌̕̚ͅ͏͓͔͕͖͙͚͐͑͒͗͛ͣͤͥͦͧͨͩͪͫͬͭͮͯ͘͜͟͢͝͞͠͡''`´‘’ʼʹʿʾ',
      'aaaaaaceeeeiiiidnoooooouuuuyaaaaaaceeeeiiiidnoooooouuuuyyaaaaaaccccccccddddeeeeeeeeeegggggggghhhhiiiiiiiiiijjkkqllllllllllnnnnnnnnnoooooorrrrrrssssssssttttttuuuuuuuuuuuuwwyyyzzzzzzsbbccddffikklnnoooppttttuuvyyzzaaiioouuuuuuuuuueaaaaggggkkoooojggnnaaooaaaaeeeeiiiioooorrrruuuusstthhdzzaaeeooooooooyylntjaccltszbueejjrryyaabbbbbbccddddddddddeeeeeeeeeeffgghhhhhhhhhhiiiikkkkkkllllllllmmmmmmnnnnnnnnoooooooopppprrrrrrrrssssssssssttttttttuuuuuuuuuuvvvvwwwwwwwwwwxxxxyyzzzzzzhtwyssaaaaaaaaaaaaaaaaaaaaaaaaeeeeeeeeeeeeeeeeiiiioooooooooooooooooooooooouuuuuuuuuuuuuuyyyyyyyyabcdefghijklmnopqrstuvwxyz                                             ' || '   '), ' {2,}', ' ', 'g'), ' '), '^the ', '')
$fn$;

CREATE INDEX IF NOT EXISTS places_label_key ON places (histmersion_place_key(label));
CREATE INDEX IF NOT EXISTS place_names_key  ON place_names (histmersion_place_key(name));
