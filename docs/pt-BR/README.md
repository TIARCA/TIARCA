# Guia completo do TIARCA 0.9.2

[← Idiomas](../README.md)

TIARCA é um cliente IRC moderno para Android, derivado do Revolution IRC e atualmente desenvolvido como projeto independente.

## Instalação e servidores
Instale o APK assinado pelo GitHub Releases. É possível configurar várias redes com endereço, porta, nick, TLS/SSL, SASL e canais de entrada automática, além de reordenar servidores. Nunca publique senhas ou credenciais SASL.

<!-- SCREENSHOT: configuração do servidor -->

## Interface, canais e conversas privadas
O menu lateral reúne servidores, canais e PVTs. Menções são contadas separadamente das mensagens não lidas. **Usuários monitorados** fica no topo e **Configurações** permanece no fim do menu `…`. Canais oferecem histórico, autocompletar e formatação mIRC. PVTs possuem ações diretas **Enviar** e **Ignorar**.

<!-- SCREENSHOT: menu lateral -->
<!-- SCREENSHOT: canal -->
<!-- SCREENSHOT: PVT -->

## WHOIS e WHOWAS
WHOIS mostra informações fornecidas pelo servidor e ações relacionadas ao usuário. WHOWAS é apresentado de forma estruturada. Os campos disponíveis dependem da rede IRC.

<!-- SCREENSHOT: WHOIS -->

## Modos de usuário e canal
Os editores mostram estado e descrição dos modos, considerando diferenças entre famílias de servidores IRC. Modos controlados pelo servidor/services são protegidos; alterações em modos de canal dependem dos seus privilégios.

<!-- SCREENSHOT: modos de usuário -->
<!-- SCREENSHOT: modos de canal -->

## Bans e exceções
Listas de bans e exceções de canal são reunidas em uma interface comum e podem ser administradas quando você possui permissões suficientes.

<!-- SCREENSHOT: bans/exceções -->

## MONITOR
Em redes compatíveis, TIARCA suporta IRC `MONITOR`. A tela de usuários monitorados gerencia a lista e mostra o estado online/offline conhecido.

<!-- SCREENSHOT: usuários monitorados -->

## Caller-ID +g e ACCEPT
`+g` pode restringir quem pode enviar mensagens privadas. TIARCA trata o numeric 718 e ACCEPT. Desde a 0.9.2, ao iniciar voluntariamente um PVT, `ACCEPT +nick` é executado automaticamente somente quando `+g` está realmente ativo.

## Busca, histórico, menções e Ignore
A busca encontra mensagens e permite ir ao contexto cronológico. Menções possuem contador próprio. Ignore filtra usuários indesejados e está disponível diretamente na barra do PVT.

<!-- SCREENSHOT: busca -->

## Arquivos e mídia
Imagens, arquivos, áudio e vídeo podem ser compartilhados por links temporários; o link resultante é enviado aos destinatários via IRC.

## Comandos IRC e Comandos rápidos
TIARCA suporta diretamente `/whowas`, `/accept`, `/invite`, `/ison`, `/userhost`, `/motd`, `/version`, `/time`, `/admin`, `/info`, `/lusers`, `/links`, `/stats`, `/knock`, `/list`, `/names` e `/who`. Os **Comandos rápidos** configuráveis incluem gatilhos como `!yt`, `!wiki`, `!calc`, `!movie`, `!ora` e `!dizionario`.

<!-- SCREENSHOT: comandos rápidos -->

## Aparência
Há tema claro/escuro, personalização de cores, fonte do chat e formato das mensagens, incluindo relógio opcional à direita.

<!-- SCREENSHOT: interface -->
<!-- SCREENSHOT: cores -->
<!-- SCREENSHOT: formato das mensagens -->

## Atualizações e backup
O verificador de atualizações é opcional e consulta os GitHub Releases oficiais. TIARCA também suporta backup criptografado/transferência do Android separadamente do backup manual do aplicativo.

## Compatibilidade e solução de problemas
Redes IRC usam servidores e extensões diferentes; recursos do servidor só funcionam quando a rede os implementa. Se não conectar, confira host, porta, TLS, nick e SASL. Em relatórios de bugs, informe versões e passos de reprodução, removendo senhas, tokens, hosts/IPs pessoais e outros dados sensíveis.

## Privacidade, origem e licença
IRC não é E2EE por padrão. TLS protege o transporte cliente-servidor, mas não transforma a conversa em criptografia ponta a ponta. TIARCA deriva do Revolution IRC de MrARM/MCMrARM e continua independentemente sob GNU GPLv3.
