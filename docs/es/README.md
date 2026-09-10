# Guía completa de TIARCA 0.9.2

[← Idiomas](../README.md)

TIARCA es un cliente IRC moderno para Android, derivado de Revolution IRC y desarrollado actualmente como proyecto independiente.

## Instalación y servidores
Instala el APK firmado desde GitHub Releases. Puedes configurar varias redes con dirección, puerto, nick, TLS/SSL, SASL y canales de entrada automática, además de reordenar los servidores. Nunca publiques contraseñas o credenciales SASL.

<!-- SCREENSHOT: configuración del servidor -->

## Interfaz, canales y privados
El panel lateral reúne servidores, canales y conversaciones privadas. Las menciones se cuentan por separado de los mensajes no leídos. **Usuarios monitorizados** aparece en la parte superior y **Ajustes** permanece al final del menú `…`. Los canales ofrecen historial, autocompletado y formato mIRC. En los privados hay acciones directas **Enviar** e **Ignorar**.

<!-- SCREENSHOT: drawer -->
<!-- SCREENSHOT: canal -->
<!-- SCREENSHOT: privado -->

## WHOIS y WHOWAS
WHOIS muestra la información que entrega el servidor y acciones relacionadas con el usuario. WHOWAS se presenta de forma estructurada. Los campos disponibles dependen de la red IRC.

<!-- SCREENSHOT: WHOIS -->

## Modos de usuario y canal
Los editores muestran el estado y la descripción de los modos, teniendo en cuenta diferencias entre familias de servidores IRC. Los modos gestionados por servidor/services se protegen y los cambios de canal dependen de tus privilegios.

<!-- SCREENSHOT: modos de usuario -->
<!-- SCREENSHOT: modos de canal -->

## Bans y excepciones
TIARCA reúne las listas de bans y excepciones de canal en una interfaz común. Con permisos suficientes se pueden gestionar las máscaras devueltas por el servidor.

<!-- SCREENSHOT: bans/excepciones -->

## MONITOR
En redes compatibles se admite IRC `MONITOR`. La pantalla **Usuarios monitorizados** gestiona la lista y muestra el estado online/offline conocido.

<!-- SCREENSHOT: usuarios monitorizados -->

## Caller-ID +g y ACCEPT
`+g` puede limitar quién puede enviarte privados. TIARCA procesa el numeric 718 y las acciones ACCEPT. Desde 0.9.2, al iniciar tú un privado, `ACCEPT +nick` se ejecuta automáticamente sólo si `+g` está realmente activo.

## Búsqueda, historial, menciones e Ignore
La búsqueda localiza mensajes y permite saltar al contexto cronológico. Las menciones tienen contador propio. Ignore filtra usuarios no deseados y está disponible directamente en la barra de los privados.

<!-- SCREENSHOT: búsqueda -->

## Archivos y multimedia
Se pueden compartir imágenes, archivos, audio y vídeo mediante enlaces temporales. El enlace resultante se envía por IRC a los destinatarios.

## Comandos IRC y Comandos rápidos
TIARCA admite directamente `/whowas`, `/accept`, `/invite`, `/ison`, `/userhost`, `/motd`, `/version`, `/time`, `/admin`, `/info`, `/lusers`, `/links`, `/stats`, `/knock`, `/list`, `/names` y `/who`. Los **Comandos rápidos** configurables incluyen activadores como `!yt`, `!wiki`, `!calc`, `!movie`, `!ora` y `!dizionario`.

<!-- SCREENSHOT: comandos rápidos -->

## Apariencia
Hay tema claro/oscuro, personalización de colores, fuente del chat y formato de mensajes, incluido un reloj opcional a la derecha.

<!-- SCREENSHOT: interfaz -->
<!-- SCREENSHOT: colores -->
<!-- SCREENSHOT: formato de mensajes -->

## Actualizaciones y copia de seguridad
El comprobador de actualizaciones es opcional y consulta las Releases oficiales de GitHub. TIARCA también admite el backup cifrado/transferencia de Android por separado de su copia manual.

## Compatibilidad y solución de problemas
Las redes IRC usan servidores y extensiones diferentes; una función sólo puede operar si la red la implementa. Si no conecta, revisa host, puerto, TLS, nick y SASL. Si un modo falla, revisa permisos. En informes de errores incluye versiones y pasos reproducibles, eliminando contraseñas, tokens, hosts/IP personales y otros datos sensibles.

## Privacidad, origen y licencia
IRC no es E2EE por defecto. TLS protege el transporte cliente-servidor, no toda la conversación extremo a extremo. TIARCA deriva de Revolution IRC de MrARM/MCMrARM y continúa de forma independiente bajo GNU GPLv3.
