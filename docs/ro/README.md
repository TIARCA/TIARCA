# Ghid complet TIARCA 0.9.2

[← Limbi](../README.md)

TIARCA este un client IRC modern pentru Android, derivat din Revolution IRC și dezvoltat în prezent ca proiect independent.

## Instalare și servere
Instalează APK-ul semnat din GitHub Releases. Poți configura mai multe rețele cu adresă, port, nickname, TLS/SSL, SASL și canale pentru conectare automată; serverele pot fi reordonate. Nu publica niciodată parole sau credențiale SASL.

![configurare server](../images/12_configurazione_server.jpg)

## Interfață, canale și conversații private
Panoul lateral conține servere, canale și PVT-uri. Mențiunile sunt numărate separat de mesajele necitite obișnuite. **Utilizatori monitorizați** este disponibil sus, iar **Setări** rămâne la finalul meniului `…`. Canalele oferă istoric, autocompletare și formatare mIRC. În PVT există acțiuni directe **Trimite** și **Ignoră**.

![panou](../images/01_drawer_menu.jpg)
![canal](../images/02_chat_canale.jpg)
![PVT](../images/03_chat_privata_pvt.jpg)

## WHOIS și WHOWAS
WHOIS afișează informațiile furnizate de server și acțiunile relevante. WHOWAS este prezentat structurat. Câmpurile disponibile depind de rețeaua IRC.

![WHOIS](../images/04_whois.jpg)

## Moduri utilizator și canal
Editoarele afișează starea și descrierea modurilor și țin cont de diferențele dintre familiile de servere IRC. Modurile controlate de server/services sunt protejate; modificarea modurilor de canal depinde de privilegii.

![moduri utilizator](../images/06_modalita_utente.jpg)
![moduri canal](../images/07_modalita_canale.jpg)

## Ban-uri și excepții
Listele de ban și excepții ale canalului sunt reunite într-o interfață comună și pot fi administrate când ai permisiunile necesare.

![ban/excepții](../images/08_ban_eccezioni.jpg)

## MONITOR
Pe rețele compatibile TIARCA suportă IRC `MONITOR`. Ecranul utilizatorilor monitorizați gestionează lista și afișează starea online/offline cunoscută.

![utilizatori monitorizați](../images/05_utenti_monitorati.jpg)

## Caller-ID +g și ACCEPT
`+g` poate limita cine îți poate trimite mesaje private. TIARCA gestionează numeric 718 și ACCEPT. Din 0.9.2, când inițiezi intenționat un PVT, `ACCEPT +nick` este trimis automat numai dacă `+g` este într-adevăr activ.

## Căutare, istoric, mențiuni și Ignore
Căutarea găsește mesaje și permite saltul la contextul cronologic. Mențiunile au contor separat. Ignore filtrează utilizatorii nedoriți și este disponibil direct în bara PVT.

![căutare](../images/09_ricerca_messaggi.jpg)

## Fișiere și media
Imagini, fișiere, audio și video pot fi partajate prin linkuri temporare; linkul rezultat este trimis destinatarilor prin IRC.

## Comenzi IRC și comenzi rapide
TIARCA suportă direct `/whowas`, `/accept`, `/invite`, `/ison`, `/userhost`, `/motd`, `/version`, `/time`, `/admin`, `/info`, `/lusers`, `/links`, `/stats`, `/knock`, `/list`, `/names` și `/who`. Comenzile rapide configurabile includ declanșatoare precum `!yt`, `!wiki`, `!calc`, `!movie`, `!ora` și `!dizionario`.

![comenzi rapide](../images/14_comandi_rapidi.jpg)

## Aspect
Sunt disponibile teme luminoasă/întunecată, personalizarea culorilor, fontului chatului și formatului mesajelor, inclusiv ora opțională în partea dreaptă.

![interfață](../images/10_impostazioni_interfaccia.jpg)
![culori](../images/13_personalizzazione_colori.jpg)
![format mesaje](../images/11_formato_messaggi.jpg)

## Actualizări și backup
Verificarea integrată a actualizărilor este opțională și folosește GitHub Releases oficiale. TIARCA suportă și backupul criptat/transferul Android separat de backupul manual TIARCA.

## Compatibilitate și depanare
Rețelele IRC folosesc servere și extensii diferite; funcțiile server-side există numai dacă rețeaua le implementează. Pentru probleme de conectare verifică host, port, TLS, nick și SASL. În rapoarte de bug include versiunile și pașii de reproducere, dar elimină parole, tokenuri, hosturi/IP-uri personale și alte date sensibile.

## Confidențialitate, origine și licență
IRC nu este implicit criptat end-to-end. TLS protejează transportul client-server, nu întreaga conversație E2EE. TIARCA derivă din Revolution IRC de MrARM/MCMrARM și continuă independent sub GNU GPLv3.
