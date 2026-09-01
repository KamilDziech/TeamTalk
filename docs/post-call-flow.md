# Flow: akcje po zakończeniu połączenia

## 1. Wykrycie zakończenia połączenia

- `CallStateReceiver` nasłuchuje `ACTION_PHONE_STATE_CHANGED` w tle
- Monitorowany jest tylko skonfigurowany SIM (lub wszystkie jeśli ustawiono "wszystkie SIM")
- Połączenie śledzone od `OFFHOOK` (odebranie) do `IDLE` (rozłączenie)

## 2. Automatyczne otwarcie kreatora (~2 sekundy po rozłączeniu)

- `PostCallNoteScreen` (kreator trzech plansz) otwiera się **bez żadnej akcji użytkownika**
- Działa w obu scenariuszach:
  - **Ekran włączony** — aplikacja otwiera się na wierzchu
  - **Ekran zablokowany/wyłączony** — ekran budzi się, aplikacja pojawia się nad ekranem blokady
- 2 sekundy opóźnienia żeby dziennik połączeń systemu zdążył się zaktualizować

## 3. Plansza 1 — z kim rozmawiałeś?

- Numer telefonu odczytany z dziennika połączeń urządzenia
- Jeśli numer jest w module Klienci — pytanie „Czy rozmawiałeś z *Imię Nazwisko*?"
  - **Tak** → plansza 2
  - **Nie — dodaj kontakt** → formularz kartoteki z wpisanym numerem; po zapisie
    kreator wraca na planszę 2 z podpiętym świeżym klientem
  - **Pomiń** → pytanie „Na pewno chcesz pominąć dodanie notatki?"; potwierdzenie
    zamyka **cały** kreator (rozmowa zostaje w historii, bez streszczenia)
- Jeśli numeru nie ma w kartotece — zamiast potwierdzania pytamy wprost o
  założenie kontaktu (**Dodaj kontakt** / **Pomiń**). Nazwa z książki telefonu, gdy
  jest, trafia do formularza jako podpowiedź imienia i nazwiska
- Wejście z karty połączenia („Dodaj notatkę") pomija tę planszę — rozmówca jest
  tam już znany (`skipContact=1`)

## 4. Zapis połączenia do historii

- Połączenie zapisywane do bazy danych aplikacji (`CallLog`)
- Jeśli klient o tym numerze nie istnieje — tworzony jest nowy klient
- Jeśli klient istnieje ale bez nazwy, a kontakt w telefonie ma nazwę — nazwa jest uzupełniana
- Nieodebrane połączenia od tego numeru są oznaczane jako zakończone (`COMPLETED`)

## 5. Plansza 2 — streść rozmowę

- Może nagrać streszczenie głosowo (rozpoznawanie mowy on-device, tekst do korekty)
- Może wpisać je z klawiatury (zakładka „Tekstowa")
- „Zapisz i dalej" tworzy `VoiceReport` powiązany z klientem i połączeniem — to on
  zasila kanał **Telefon** w module Komunikacja i zakładkę „Telefon" na karcie
  klienta w panelu

## 6. Plansza 3 — czy utworzyć zadanie?

- **Nie** → kreator się kończy, streszczenie jest już zapisane
- **Tak** → skrócony kreator zadania: **zespół → osoba → priorytet → termin**
  (`create_task?mode=short`). Tytuł, opis (streszczenie) i klient są wypełnione
  z rozmowy, więc trzech pierwszych plansz pełnego kreatora nie pokazujemy

## 7. Fallback gdy brak uprawnień

- Jeśli uprawnienie `SYSTEM_ALERT_WINDOW` ("Wyświetlaj nad innymi aplikacjami") nie jest nadane — zamiast auto-otwarcia pojawia się powiadomienie wysokiego priorytetu, które użytkownik musi kliknąć
- Uprawnienie jest wymagane przez Android 14 (`targetSdk=34`) jako wyjątek od restrykcji BAL (Background Activity Launch)
