# Pełny przewodnik po TIARCA 0.9.2

[← Języki](../README.md)

TIARCA to nowoczesny klient IRC dla Androida, wywodzący się z Revolution IRC i rozwijany jako niezależny projekt.

## Instalacja i serwery
Zainstaluj podpisany APK z GitHub Releases. Można skonfigurować wiele sieci: adres, port, nick, TLS/SSL, SASL i kanały do automatycznego dołączenia; serwery można ręcznie sortować. Nigdy nie publikuj haseł ani danych SASL.

<!-- SCREENSHOT: konfiguracja serwera -->

## Interfejs, kanały i rozmowy prywatne
Panel boczny zawiera serwery, kanały i PVT. Wzmianki są liczone oddzielnie od zwykłych nieprzeczytanych wiadomości. **Monitorowani użytkownicy** są dostępni u góry, a **Ustawienia** pozostają na dole menu `…`. Kanały obsługują historię, autouzupełnianie i formatowanie mIRC. W PVT dostępne są bezpośrednie akcje **Wyślij** i **Ignoruj**.

<!-- SCREENSHOT: panel -->
<!-- SCREENSHOT: kanał -->
<!-- SCREENSHOT: PVT -->

## WHOIS i WHOWAS
WHOIS pokazuje informacje zwrócone przez serwer i odpowiednie akcje. WHOWAS jest prezentowany strukturalnie. Dostępne pola zależą od sieci IRC.

<!-- SCREENSHOT: WHOIS -->

## Tryby użytkownika i kanału
Edytory pokazują stan i opis trybów oraz uwzględniają różnice pomiędzy rodzinami serwerów IRC. Tryby kontrolowane przez serwer/services są chronione; zmiany trybów kanału zależą od uprawnień.

<!-- SCREENSHOT: tryby użytkownika -->
<!-- SCREENSHOT: tryby kanału -->

## Bany i wyjątki
Listy banów i wyjątków kanału są dostępne we wspólnym interfejsie i mogą być modyfikowane przy odpowiednich uprawnieniach.

<!-- SCREENSHOT: bany/wyjątki -->

## MONITOR
Na zgodnych sieciach TIARCA obsługuje IRC `MONITOR`. Ekran monitorowanych użytkowników zarządza listą i pokazuje znany stan online/offline.

<!-- SCREENSHOT: monitorowani użytkownicy -->

## Caller-ID +g i ACCEPT
`+g` może ograniczać osoby mogące wysyłać prywatne wiadomości. TIARCA obsługuje numeric 718 i ACCEPT. Od 0.9.2 przy świadomym rozpoczęciu PVT `ACCEPT +nick` jest wykonywany automatycznie tylko wtedy, gdy `+g` jest rzeczywiście aktywny.

## Wyszukiwanie, historia, wzmianki i Ignore
Wyszukiwanie przenosi do znalezionej wiadomości w kontekście chronologicznym. Wzmianki mają osobny licznik. Ignore filtruje niechcianych użytkowników i jest dostępne bezpośrednio w pasku PVT.

<!-- SCREENSHOT: wyszukiwanie -->

## Pliki i multimedia
Obrazy, pliki, audio i wideo mogą być udostępniane przez tymczasowe linki wysyłane następnie przez IRC.

## Polecenia IRC i szybkie polecenia
TIARCA bezpośrednio obsługuje m.in. `/whowas`, `/accept`, `/invite`, `/ison`, `/userhost`, `/motd`, `/version`, `/time`, `/admin`, `/info`, `/lusers`, `/links`, `/stats`, `/knock`, `/list`, `/names` i `/who`. Konfigurowalne szybkie polecenia obejmują `!yt`, `!wiki`, `!calc`, `!movie`, `!ora` i `!dizionario`.

<!-- SCREENSHOT: szybkie polecenia -->

## Wygląd
Dostępny jest jasny i ciemny motyw, personalizacja kolorów, czcionki czatu i formatu wiadomości, w tym opcjonalny zegar po prawej stronie.

<!-- SCREENSHOT: interfejs -->
<!-- SCREENSHOT: kolory -->
<!-- SCREENSHOT: format wiadomości -->

## Aktualizacje i kopie zapasowe
Opcjonalny moduł aktualizacji sprawdza oficjalne GitHub Releases. TIARCA obsługuje również szyfrowaną kopię Android/transfer urządzenia niezależnie od ręcznej kopii TIARCA.

## Zgodność i rozwiązywanie problemów
Sieci IRC używają różnych serwerów i rozszerzeń; funkcja serwerowa działa tylko, jeśli sieć ją implementuje. Przy problemach z połączeniem sprawdź host, port, TLS, nick i SASL. W zgłoszeniach błędów podaj wersje i kroki odtworzenia, usuwając hasła, tokeny, prywatne hosty/IP i inne dane wrażliwe.

## Prywatność, pochodzenie i licencja
IRC nie jest domyślnie szyfrowany end-to-end. TLS chroni transport klient-serwer, ale nie czyni IRC E2EE. TIARCA bazuje na Revolution IRC autorstwa MrARM/MCMrARM i jest niezależnie rozwijana na licencji GNU GPLv3.
