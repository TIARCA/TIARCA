# Guide complet de TIARCA 0.9.3

[← Langues](../README.md)

TIARCA est un client IRC moderne pour Android, issu de Revolution IRC et désormais développé comme projet indépendant.

## Installation et serveurs
Installez l'APK signé depuis GitHub Releases. Plusieurs réseaux peuvent être configurés avec adresse, port, pseudo, TLS/SSL, SASL et canaux à rejoindre automatiquement; les serveurs peuvent être réordonnés. Ne publiez jamais mots de passe ou identifiants SASL.

![configuration serveur](../images/12_configurazione_server.jpg)

## Interface, salons et privés
Le tiroir regroupe serveurs, salons et conversations privées. Les mentions sont comptées séparément des messages non lus. **Utilisateurs surveillés** est accessible en haut et **Paramètres** reste en bas du menu `…`. Les salons proposent historique, autocomplétion et formatage mIRC. Les privés disposent des actions directes **Envoyer** et **Ignorer**.

![tiroir](../images/01_drawer_menu.jpg)
![salon](../images/02_chat_canale.jpg)
![privé](../images/03_chat_privata_pvt.jpg)

## WHOIS et WHOWAS
WHOIS présente les informations fournies par le serveur et les actions utilisateur pertinentes. WHOWAS est affiché sous forme structurée. Les champs disponibles varient selon le réseau.

![WHOIS](../images/04_whois.jpg)

## Modes utilisateur et salon
Les éditeurs affichent état et description des modes en tenant compte des différences entre familles de serveurs IRC. Les modes contrôlés par le serveur/services sont protégés; les modifications de salon dépendent de vos privilèges.

![modes utilisateur](../images/06_modalita_utente.jpg)
![modes salon](../images/07_modalita_canale.jpg)

## Bans et exceptions
Les listes de bans et d'exceptions sont réunies dans une interface commune et peuvent être gérées si vos droits le permettent.

![bans/exceptions](../images/08_ban_eccezioni.jpg)

## MONITOR
Sur les réseaux compatibles, TIARCA prend en charge IRC `MONITOR`. L'écran dédié gère la liste et affiche l'état en ligne/hors ligne connu.

![utilisateurs surveillés](../images/05_utenti_monitorati.jpg)

## Caller-ID +g et ACCEPT
`+g` peut limiter les utilisateurs autorisés à vous écrire en privé. TIARCA gère le numeric 718 et ACCEPT. Depuis 0.9.2, lorsqu'un privé est volontairement initié par vous, `ACCEPT +nick` est automatique uniquement si `+g` est réellement actif.

## Recherche, historique, mentions et Ignore
La recherche retrouve un message et permet de revenir à son contexte chronologique. Les mentions ont leur propre compteur. Ignore filtre les utilisateurs indésirables et l'action est directement disponible dans la barre des privés.

![recherche](../images/09_ricerca_messaggi.jpg)

## Fichiers et médias
Images, fichiers, audio et vidéo peuvent être partagés au moyen de liens temporaires; le lien est ensuite envoyé via IRC aux destinataires.

## Commandes IRC et commandes rapides
TIARCA gère directement `/whowas`, `/accept`, `/invite`, `/ison`, `/userhost`, `/motd`, `/version`, `/time`, `/admin`, `/info`, `/lusers`, `/links`, `/stats`, `/knock`, `/list`, `/names` et `/who`. Les **Commandes rapides** configurables incluent notamment `!yt`, `!wiki`, `!calc`, `!movie`, `!ora` et `!dizionario`.

![commandes rapides](../images/14_comandi_rapidi.jpg)

## Apparence
Thèmes clair/sombre, couleurs, police du chat et format des messages sont personnalisables, y compris l'horloge optionnelle à droite.

![interface](../images/10_impostazioni_interfaccia.jpg)
![couleurs](../images/13_personalizzazione_colori.jpg)
![format messages](../images/11_formato_messaggi.jpg)

## Mises à jour et sauvegarde
La vérification intégrée des mises à jour est optionnelle et consulte les Releases GitHub officielles. TIARCA prend aussi en charge la sauvegarde Android chiffrée/transfert d'appareil séparément de sa sauvegarde manuelle.

## Compatibilité et dépannage
Les réseaux IRC utilisent différents serveurs et extensions; une fonction côté serveur n'est disponible que si le réseau l'implémente. En cas d'échec de connexion, vérifiez hôte, port, TLS, pseudo et SASL. Pour un bug, fournissez versions et étapes de reproduction mais supprimez mots de passe, tokens, hôtes/IP personnels et autres données sensibles.

## Confidentialité, origine et licence
IRC n'est pas E2EE par défaut. TLS protège le transport client-serveur, pas l'ensemble de la conversation de bout en bout. TIARCA dérive de Revolution IRC de MrARM/MCMrARM et poursuit son développement indépendant sous GNU GPLv3.
