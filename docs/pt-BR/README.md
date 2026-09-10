# Guia completo do TIARCA 0.9.3

[← Idiomas](../README.md)

TIARCA é um cliente IRC moderno para Android, derivado do Revolution IRC e atualmente desenvolvido como projeto independente.

## Instalação e servidores
Instale o APK assinado pelo GitHub Releases. É possível configurar várias redes com endereço, porta, nick, TLS/SSL, SASL e canais de entrada automática, além de reordenar servidores. Nunca publique senhas ou credenciais SASL.

![configuração do servidor](../images/12_configurazione_server.jpg)

## Interface, canais e conversas privadas
O menu lateral reúne servidores, canais e PVTs. Menções são contadas separadamente das mensagens não lidas. **Usuários monitorados** fica no topo e **Configurações** permanece no fim do menu `…`. Canais oferecem histórico, autocompletar e formatação mIRC. PVTs possuem ações diretas **Enviar** e **Ignorar**.

![menu lateral](../images/01_drawer_menu.jpg)
![canal](../images/02_chat_canale.jpg)
![PVT](../images/03_chat_privata_pvt.jpg)

## WHOIS e WHOWAS
WHOIS mostra informações fornecidas pelo servidor e ações relacionadas ao usuário. WHOWAS é apresentado de forma estruturada. Os campos disponíveis dependem da rede IRC.

![WHOIS](../images/04_whois.jpg)

## Modos de usuário e canal
Os editores mostram estado e descrição dos modos, considerando diferenças entre famílias de servidores IRC. Modos controlados pelo servidor/services são protegidos; alterações em modos de canal dependem dos seus privilégios.

![modos de usuário](../images/06_modalita_utente.jpg)
![modos de canal](../images/07_modalita_canale.jpg)

## Bans e exceções
Listas de bans e exceções de canal são reunidas em uma interface comum e podem ser administradas quando você possui permissões suficientes.

![bans/exceções](../images/08_ban_eccezioni.jpg)

## MONITOR
Em redes compatíveis, TIARCA suporta IRC `MONITOR`. A tela de usuários monitorados gerencia a lista e mostra o estado online/offline conhecido.

![usuários monitorados](../images/05_utenti_monitorati.jpg)

## Caller-ID +g e ACCEPT
`+g` pode restringir quem pode enviar mensagens privadas. TIARCA trata o numeric 718 e ACCEPT. Desde a 0.9.2, ao iniciar voluntariamente um PVT, `ACCEPT +nick` é executado automaticamente somente quando `+g` está realmente ativo.

## Busca, histórico, menções e Ignore
A busca encontra mensagens e permite ir ao contexto cronológico. Menções possuem contador próprio. Ignore filtra usuários indesejados e está disponível diretamente na barra do PVT.

![busca](../images/09_ricerca_messaggi.jpg)

## Arquivos e mídia
Imagens, arquivos, áudio e vídeo podem ser compartilhados por links temporários; o link resultante é enviado aos destinatários via IRC.

## Comandos IRC e Comandos rápidos
TIARCA suporta diretamente `/whowas`, `/accept`, `/invite`, `/ison`, `/userhost`, `/motd`, `/version`, `/time`, `/admin`, `/info`, `/lusers`, `/links`, `/stats`, `/knock`, `/list`, `/names` e `/who`. Os **Comandos rápidos** configuráveis incluem gatilhos como `!yt`, `!wiki`, `!calc`, `!movie`, `!ora` e `!dizionario`.

![comandos rápidos](../images/14_comandi_rapidi.jpg)

## Aparência
Há tema claro/escuro, personalização de cores, fonte do chat e formato das mensagens, incluindo relógio opcional à direita.

![interface](../images/10_impostazioni_interfaccia.jpg)
![cores](../images/13_personalizzazione_colori.jpg)
![formato das mensagens](../images/11_formato_messaggi.jpg)

## Atualizações e backup
O verificador de atualizações é opcional e consulta os GitHub Releases oficiais. TIARCA também suporta backup criptografado/transferência do Android separadamente do backup manual do aplicativo.

## Compatibilidade e solução de problemas
Redes IRC usam servidores e extensões diferentes; recursos do servidor só funcionam quando a rede os implementa. Se não conectar, confira host, porta, TLS, nick e SASL. Em relatórios de bugs, informe versões e passos de reprodução, removendo senhas, tokens, hosts/IPs pessoais e outros dados sensíveis.

## Privacidade, origem e licença
IRC não é E2EE por padrão. TLS protege o transporte cliente-servidor, mas não transforma a conversa em criptografia ponta a ponta. TIARCA deriva do Revolution IRC de MrARM/MCMrARM e continua independentemente sob GNU GPLv3.
