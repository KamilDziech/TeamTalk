# Ikony TeamTalk

Paleta wg ksiegi znaku EKOTAK v1.2023: zielen Pantone 802 C `#44D62C` + czern `#080808`.

## Ikona wdrozona

`ic_ekotak_gears_512.svg` — trzy zazebione kola w petli dwoch strzalek, czern na
zieleni. Ten sam znak, co w panelu webowym (`ekotak-app/web/public/ekotak-icon.svg`
i komponent `EkotakIcon`), zeby launcher i aplikacja mialy jedna ikone.

Warstwy adaptive icon powstaja z `gen-launcher-icon.sh` — skrypt zapisuje
`res/drawable/ic_launcher_{background,foreground,monochrome}.xml`. Plikow XML nie
edytujemy recznie; zmiana geometrii = zmiana skryptu i ponowne uruchomienie.
Znak jest skalowany do 0,602 — miesci sie w strefie bezpiecznej 72/108 dp
(zajmuje ok. 50 z 72 dp) z widocznym zielonym marginesem dookola.

`minSdk = 26`, wiec fallbackowe PNG w `mipmap-{m..xxxhdpi}` nie sa potrzebne —
kazde urzadzenie dostaje adaptive icon.

## Warstwa monochrome (Android 13+)

Uproszczona: bez aureoli oddzielajacych kola, bo w jednym kolorze nie ma czym
"wycinac" przerw. Zebatki zlewaja sie w jedna sylwetke, otwory w piastach sa
wycinane regula `evenOdd`. Czytelne, ale to swiadomy kompromis.

## Ikona powiadomienia

`ic_stat_ekotak.svg` -> `res/drawable/ic_stat_ekotak.xml`. Biala sylwetka bez
tla (system ja koloruje), akcent ustawiony w `NotificationHelper` przez
`setColor(R.color.ekotak_green)`.

Znak jest tu ZREDUKOWANY: petla strzalek + jedna zebatka zamiast trzech.
Trzy zazebione kola przy 24 dp zlewaja sie w plame. Proporcje narysowane od
nowa w siatce 24 (kreska 2 dp), a nie przeskalowane z 800 - przeskalowana
kreska mialaby 0,8 dp i znikala na pasku stanu. Z tego samego powodu odpadly
dwie krotkie kreski w przerwach petli.

## Wczesniejsze koncepty (niewdrozone)

| Plik | Zastosowanie |
|---|---|
| `ic_teamtalk_a_512.svg` | Koncept A "Warsztat" |
| `ic_teamtalk_b_512.svg` | Koncept B "Obieg" - petla procesu wokol zebatki |
| `ic_teamtalk_c_512.svg` | Koncept C "Glos maszyny" - mikrofon w koronie zebatki |
| `ic_teamtalk_d_512.svg` | Koncept D "Sygnal" - dymek z fala glosu |
| `ic_launcher_foreground.svg` | Warstwa foreground dla konceptu A |
| `ic_launcher_monochrome.svg` | Warstwa monochrome dla konceptu A |

`ic_stat_teamtalk.svg` to ikona powiadomienia dla konceptu A — zastapiona przez
`ic_stat_ekotak.svg`, zostawiona razem z reszta konceptu A.
