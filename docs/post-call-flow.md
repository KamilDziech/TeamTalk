# Flow: akcje po zakończeniu połączenia

## 1. Wykrycie zakończenia połączenia

- `CallStateReceiver` nasłuchuje `ACTION_PHONE_STATE_CHANGED` w tle
- Monitorowany jest tylko skonfigurowany SIM (lub wszystkie jeśli ustawiono "wszystkie SIM")
- Połączenie śledzone od `OFFHOOK` (odebranie) do `IDLE` (rozłączenie)

## 2. Automatyczne otwarcie ekranu notatki (~2 sekundy po rozłączeniu)

- `PostCallNoteScreen` otwiera się **bez żadnej akcji użytkownika**
- Działa w obu scenariuszach:
  - **Ekran włączony** — aplikacja otwiera się na wierzchu
  - **Ekran zablokowany/wyłączony** — ekran budzi się, aplikacja pojawia się nad ekranem blokady
- 2 sekundy opóźnienia żeby dziennik połączeń systemu zdążył się zaktualizować

## 3. Ekran notatki pokazuje dane rozmówcy

- Numer telefonu odczytany z dziennika połączeń urządzenia
- Jeśli numer jest w bazie klientów — pokazuje nazwę klienta
- Jeśli numer nieznany — pokazuje sam numer lub "Nieznany numer"

## 4. Zapis połączenia do historii

- Połączenie zapisywane do bazy danych aplikacji (`CallLog`)
- Jeśli klient o tym numerze nie istnieje — tworzony jest nowy klient
- Jeśli klient istnieje ale bez nazwy, a kontakt w telefonie ma nazwę — nazwa jest uzupełniana
- Nieodebrane połączenia od tego numeru są oznaczane jako zakończone (`COMPLETED`)

## 5. Użytkownik dodaje notatkę

- Może wpisać notatkę tekstową
- Może nagrać notatkę głosową (auto-transkrypcja)
- Może pominąć ekran (`Pomiń`)

## 6. Fallback gdy brak uprawnień

- Jeśli uprawnienie `SYSTEM_ALERT_WINDOW` ("Wyświetlaj nad innymi aplikacjami") nie jest nadane — zamiast auto-otwarcia pojawia się powiadomienie wysokiego priorytetu, które użytkownik musi kliknąć
- Uprawnienie jest wymagane przez Android 14 (`targetSdk=34`) jako wyjątek od restrykcji BAL (Background Activity Launch)
