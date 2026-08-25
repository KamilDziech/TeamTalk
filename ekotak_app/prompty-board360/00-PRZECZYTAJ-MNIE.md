# board360 ↔ TeamTalk — prompty dla Claude Code

Cześć! To zestaw gotowych poleceń, które podłączą mobilną aplikację **TeamTalk** (na telefony serwisantów) do Twojej aplikacji webowej **board360**. Nie musisz umieć programować — całą pracę wykona **Claude Code**. Ty tylko kopiujesz treść kolejnych plików i wklejasz do Claude Code.

## Co będzie potrzebne
- Claude Code uruchomiony **w katalogu Twojego repozytorium board360** (na serwerze VPS: `/opt/ekotak/app`).
- Nic więcej — każdy prompt sam pilnuje zasad bezpieczeństwa.

## Jak z tego korzystać — krok po kroku
1. Uruchom Claude Code w katalogu `/opt/ekotak/app`.
2. Otwórz plik **`A1-logowanie-mobilne.md`**, zaznacz i skopiuj **całą** jego treść, wklej do Claude Code i wyślij.
3. Poczekaj, aż Claude Code napisze, że **skończył** i podsumuje po polsku. Przeczytaj podsumowanie.
4. Przejdź do kolejnego pliku i powtórz. **Zachowaj kolejność:**
   - `A1-logowanie-mobilne.md`
   - `A2-model-danych-telefonia.md`
   - `A3-modul-telephony.md`
   - `A4-uprawnienia.md`
   - `A5-konfiguracja-i-uruchomienie.md`
5. Gdy przejdziesz wszystkie pięć — daj znać autorowi aplikacji mobilnej (Kamilowi), że backend jest gotowy do testów.

## Ważne, żeby się nie zepsuło
- Uruchamiaj **jeden plik na raz** i czekaj na zakończenie, zanim wkleisz następny.
- Każdy prompt każe Claude Code pracować na osobnej gałęzi `feature/mobile-teamtalk` i **nie ruszać produkcji** (ekotak.app) ani bazy produkcyjnej. To celowe — najpierw testujemy bezpiecznie.
- Jeśli Claude Code o coś zapyta albo napisze, że czegoś nie może zrobić bezpiecznie — **nie zgaduj**. Skopiuj jego pytanie i prześlij Kamilowi.
- Jeśli Claude Code zgłosi błąd, którego nie umie naprawić — skopiuj cały komunikat i prześlij Kamilowi.

## Co robi każdy krok (w skrócie)
- **A1** — dodaje logowanie dla aplikacji mobilnej (mobilka dostaje „przepustkę”/token do rozmowy z serwerem).
- **A2** — dodaje do bazy danych miejsce na historię połączeń i notatki głosowe.
- **A3** — dodaje funkcje serwera: zapisywanie połączeń, notatek i nagrań.
- **A4** — nadaje serwisantom prawo do korzystania z tych funkcji.
- **A5** — ustawienia końcowe i bezpieczne uruchomienie na wersji testowej.

Po zakończeniu wszystkie zmiany będą na gałęzi `feature/mobile-teamtalk` (nie na produkcji). Finalne wdrożenie na produkcję zrobimy wspólnie po testach.
