(function () {
  var textCopy = {};

  function add(source, portuguese, spanish) {
    textCopy[source] = { 'pt-BR': portuguese, es: spanish };
  }

  add('Language', 'Idioma', 'Idioma');
  add('Features', 'Recursos', 'Funciones');
  add('Control', 'Controle', 'Controles');
  add('Content', 'Conteúdo', 'Contenido');
  add('Look & feel', 'Aparência', 'Aspecto');
  add('Possibilities', 'Possibilidades', 'Posibilidades');
  add('Customize', 'Personalizar', 'Personalizar');
  add('Compatibility', 'Compatibilidade', 'Compatibilidad');
  add('Install', 'Instalação', 'Instalación');
  add('Download', 'Baixar', 'Descargar');
  add('The most customizable media controls on Wear OS', 'Os controles de mídia mais personalizáveis para Wear OS', 'Los controles multimedia más personalizables para Wear OS');
  add('Svartifoss reads whatever\'s playing on your phone — music, podcasts, audiobooks or any other media app — and puts playback, search, queues, lyrics and metadata on your wrist. Every button, gesture, screen and visual layer can be shaped around the way you use your watch.', 'O Svartifoss lê o que estiver sendo reproduzido no telefone — músicas, podcasts, audiolivros ou qualquer outro app de mídia — e coloca reprodução, busca, filas, letras e metadados no seu pulso. Cada botão, gesto, tela e camada visual pode ser adaptado à forma como você usa o relógio.', 'Svartifoss lee lo que se está reproduciendo en tu teléfono — música, podcasts, audiolibros o cualquier otra app multimedia — y lleva la reproducción, búsqueda, colas, letras y metadatos a tu muñeca. Cada botón, gesto, pantalla y capa visual puede adaptarse a la forma en que usas tu reloj.');
  add('Download Svartifoss', 'Baixar Svartifoss', 'Descargar Svartifoss');
  add('View source on GitHub', 'Ver código-fonte no GitHub', 'Ver código fuente en GitHub');
  add('Downloads', 'Downloads', 'Descargas');
  add('Latest version', 'Versão mais recente', 'Última versión');
  add('GitHub stars', 'Estrelas no GitHub', 'Estrellas en GitHub');
  add('Watch faces', 'Faces do relógio', 'Esferas del reloj');

  add('How it works', 'Como funciona', 'Cómo funciona');
  add('Every button, gesture, and screen is yours to assign from the phone app. The watch mirrors it over the local Wearable Data Layer — no account or Svartifoss server. Beyond your two devices, the app talks to GitHub for the optional update check and only when you explicitly open the Community themes gallery, to Firebase for diagnostics and occasional developer announcement notifications, and — only if you opt in — to the streaming service itself to fetch a saved shortcut\'s cover art. Crashlytics reports, announcement notifications, and shortcut artwork can all be switched off under Data & support → Privacy or Apps.', 'Você pode atribuir cada botão, gesto e tela pelo app do telefone. O relógio recebe tudo pela Wearable Data Layer local — sem conta ou servidor do Svartifoss. Além dos seus dois dispositivos, o app acessa o GitHub para verificar atualizações opcionalmente e somente quando você abre a galeria de temas da comunidade, o Firebase para diagnósticos e avisos ocasionais do desenvolvedor e — apenas se você permitir — o próprio serviço de streaming para buscar a capa de um atalho salvo. Relatórios do Crashlytics, avisos e capas dos atalhos podem ser desativados em Dados e suporte → Privacidade ou Apps.', 'Puedes asignar cada botón, gesto y pantalla desde la app del teléfono. El reloj lo refleja mediante la Wearable Data Layer local — sin cuenta ni servidor de Svartifoss. Fuera de tus dos dispositivos, la app contacta con GitHub para comprobar actualizaciones opcionales y solo cuando abres la galería de temas de la comunidad, con Firebase para diagnósticos y avisos ocasionales del desarrollador y — solo si lo permites — con el propio servicio de streaming para obtener la portada de un acceso directo guardado. Los informes de Crashlytics, avisos y portadas de accesos directos se pueden desactivar en Datos y soporte → Privacidad o Apps.');
  add('Assign on the phone', 'Configure no telefone', 'Configurar en el teléfono');
  add('Live on the watch', 'Ao vivo no relógio', 'En vivo en el reloj');

  add('What you get', 'O que você recebe', 'Qué obtienes');
  add('Nothing about Svartifoss is fixed. Pick what each input does, how the screen looks, and what shows up when nothing\'s bound to a button.', 'Nada no Svartifoss é fixo. Escolha o que cada entrada faz, como a tela aparece e o que é mostrado quando nada está vinculado a um botão.', 'Nada en Svartifoss es fijo. Elige qué hace cada entrada, cómo se ve la pantalla y qué aparece cuando nada está asignado a un botón.');
  add('Every surface, configurable.', 'Todas as superfícies, configuráveis.', 'Cada superficie, configurable.');
  add('Now-playing screen', 'Tela de reprodução', 'Pantalla de reproducción');
  add('Album art, live position, transport controls and a draggable seek surface. Choose from twenty in-app faces, including Classic, Expressive, Poster, Studio, Verse and Metadata — each one rebuilt around the cover that\'s playing.', 'Capa do álbum, posição ao vivo, controles de reprodução e uma superfície de busca arrastável. Escolha entre vinte faces no app, incluindo Classic, Expressive, Poster, Studio, Verse e Metadata — cada uma reconstruída em torno da capa que está tocando.', 'Portada del álbum, posición en vivo, controles de reproducción y una superficie de búsqueda arrastrable. Elige entre veinte esferas dentro de la app, como Classic, Expressive, Poster, Studio, Verse y Metadata — cada una reconstruida alrededor de la portada que está sonando.');
  add('Configurable input', 'Entrada configurável', 'Entrada configurable');
  add('Physical buttons, screen quadrants, swipes, the center tap, digital crown, bezel, mini buttons and supported double-pinch gestures can all run different actions while music is playing or stopped.', 'Botões físicos, quadrantes da tela, gestos de deslizar, toque no centro, coroa digital, borda, mini botões e gestos de pinça dupla compatíveis podem executar ações diferentes com a música tocando ou parada.', 'Los botones físicos, cuadrantes de pantalla, deslizamientos, toque central, corona digital, bisel, mini botones y gestos de doble pellizco compatibles pueden ejecutar acciones distintas mientras la música suena o está detenida.');
  add('Quick-actions panel', 'Painel de ações rápidas', 'Panel de acciones rápidas');
  add('Double-tap to open a configurable panel with three round slots and one wide row. Pick an action for every slot, or mirror the real actions and icons published by the current player\'s notification.', 'Toque duas vezes para abrir um painel configurável com três espaços redondos e uma linha larga. Escolha uma ação para cada espaço ou use as ações e os ícones publicados pela notificação do player atual.', 'Toca dos veces para abrir un panel configurable con tres espacios redondos y una fila ancha. Elige una acción para cada espacio o refleja las acciones e iconos publicados por la notificación del reproductor actual.');
  add('Queue & history', 'Fila e histórico', 'Cola e historial');
  add('Browse a live queue with paging and artwork when the player exposes one, jump to an exact item, or fall back honestly to locally tracked listening history when it doesn\'t.', 'Navegue por uma fila ao vivo com paginação e capas quando o player oferecer uma, pule para um item específico ou use honestamente o histórico de reprodução acompanhado localmente quando ele não oferecer uma fila.', 'Explora una cola en vivo con páginas y portadas cuando el reproductor la expone, salta a un elemento concreto o usa el historial de escucha local cuando no existe una cola.');
  add('Glanceable surfaces', 'Superfícies rápidas', 'Superficies de vistazo');
  add('A media Tile with transport and ±10-second seek, a second Tile for saved streaming shortcuts, and an album-art/title complication for the watch face you\'re already using.', 'Um Tile de mídia com controles e busca de ±10 segundos, um segundo Tile para atalhos de streaming salvos e uma complicação de capa/título para a face que você já usa.', 'Un Tile multimedia con controles y búsqueda de ±10 segundos, un segundo Tile para accesos directos de streaming guardados y una complicación de portada/título para la esfera que ya usas.');
  add('Search & playlists', 'Busca e playlists', 'Búsqueda y listas');
  add('Search by voice or keyboard, browse the player\'s library, replay or remove search history, and open saved links to tracks, albums, playlists, artists, shows and mixes.', 'Busque por voz ou teclado, navegue pela biblioteca do player, repita ou remova o histórico de buscas e abra links salvos de faixas, álbuns, playlists, artistas, programas e mixes.', 'Busca por voz o teclado, explora la biblioteca del reproductor, repite o elimina el historial de búsqueda y abre enlaces guardados de canciones, álbumes, listas, artistas, programas y mezclas.');
  add('Full action menu', 'Menu completo de ações', 'Menú completo de acciones');
  add('A full-screen list for anything not bound to a button or gesture — nothing is ever more than one extra tap away.', 'Uma lista em tela cheia para tudo que não estiver vinculado a um botão ou gesto — nada fica a mais de um toque extra.', 'Una lista a pantalla completa para todo lo que no esté asignado a un botón o gesto — nada queda a más de un toque adicional.');
  add('Works with any player', 'Funciona com qualquer player', 'Funciona con cualquier reproductor');
  add('Any app exposing a standard Android media session can provide basic control. Queues, libraries, search, likes and other extras appear when that player publishes the relevant Android capability.', 'Qualquer app que exponha uma sessão de mídia padrão do Android pode oferecer controles básicos. Filas, bibliotecas, buscas, curtidas e outros extras aparecem quando o player publica o recurso correspondente do Android.', 'Cualquier app que exponga una sesión multimedia estándar de Android puede ofrecer controles básicos. Las colas, bibliotecas, búsquedas, favoritos y otros extras aparecen cuando el reproductor publica la capacidad correspondiente de Android.');

  add('Real player surfaces', 'Superfícies reais do player', 'Superficies reales del reproductor');
  add('Tap any image to enlarge', 'Toque em qualquer imagem para ampliar', 'Toca cualquier imagen para ampliar');
  add('Every action, on your terms.', 'Cada ação, do seu jeito.', 'Cada acción, a tu manera.');
  add('Mini buttons', 'Mini botões', 'Mini botones');
  add('Real player surface', 'Superfície real do player', 'Superficie real del reproductor');
  add('Edge seek', 'Busca pela borda', 'Búsqueda en el bisel');
  add('Drag around the bezel', 'Arraste ao redor da borda', 'Arrastra alrededor del bisel');
  add('Quick actions', 'Ações rápidas', 'Acciones rápidas');
  add('Double-tap from the player', 'Toque duas vezes no player', 'Toca dos veces desde el reproductor');
  add('Playback', 'Reprodução', 'Reproducción');
  add('More than play and pause.', 'Mais que reproduzir e pausar.', 'Mucho más que reproducir y pausar.');
  add('Skip, restart, stop, seek by percentage, jump forward or back, change playback speed, choose shuffle and repeat modes, or run a player-specific like action.', 'Avance, reinicie, pare, busque por porcentagem, pule para frente ou para trás, altere a velocidade, escolha os modos aleatório e repetição ou execute a ação de curtir específica do player.', 'Avanza, reinicia, detén, busca por porcentaje, salta hacia delante o atrás, cambia la velocidad, elige los modos aleatorio y repetición o ejecuta la acción de favorito del reproductor.');
  add('Inputs', 'Entradas', 'Entradas');
  add('Use the controls your watch actually has.', 'Use os controles que seu relógio realmente tem.', 'Usa los controles que realmente tiene tu reloj.');
  add('Physical buttons, single/double/long presses, touch zones, swipes, center actions, mini buttons and — on compatible watches — crown, bezel or double-pinch input.', 'Botões físicos, toques simples/duplos/longos, zonas de toque, deslizes, ações no centro, mini botões e — em relógios compatíveis — entradas pela coroa, borda ou pinça dupla.', 'Botones físicos, pulsaciones simples/dobles/largas, zonas táctiles, deslizamientos, acciones centrales, mini botones y — en relojes compatibles — entradas mediante corona, bisel o doble pellizco.');
  add('States', 'Estados', 'Estados');
  add('One setup while playing. Another when idle.', 'Uma configuração durante a reprodução. Outra quando está parado.', 'Una configuración mientras reproduce. Otra cuando está detenido.');
  add('Music playing and No playback are separate configurations. The same input can skip a track now and open a saved playlist when playback has stopped.', 'Música tocando e Sem reprodução são configurações separadas. A mesma entrada pode pular uma faixa agora e abrir uma playlist salva quando a reprodução parar.', 'Música reproduciéndose y Sin reproducción son configuraciones separadas. La misma entrada puede saltar una canción ahora y abrir una lista guardada cuando la reproducción se detenga.');
  add('Action menu', 'Menu de ações', 'Menú de acciones');
  add('Keep the rest one tap away.', 'Deixe o restante a um toque.', 'Deja el resto a un toque.');
  add('Open queue, lyrics, volume, progress or face picker; browse a library; launch an app; run Tasker; or start a streaming shortcut without reserving a physical button.', 'Abra a fila, letras, volume, progresso ou seletor de face; navegue por uma biblioteca; inicie um app; execute o Tasker; ou abra um atalho de streaming sem ocupar um botão físico.', 'Abre la cola, letras, volumen, progreso o selector de esfera; explora una biblioteca; inicia una app; ejecuta Tasker; o abre un acceso directo de streaming sin reservar un botón físico.');

  add('Content on your wrist', 'Conteúdo no seu pulso', 'Contenido en tu muñeca');
  add('More than a now-playing screen.', 'Mais que uma tela de reprodução.', 'Más que una pantalla de reproducción.');
  add('Svartifoss stays honest about what the active player makes available: a live queue when there is one, history when there is not, and optional search, lyrics and details only where the phone can retrieve them.', 'O Svartifoss é transparente sobre o que o player ativo oferece: uma fila ao vivo quando existir, histórico quando não existir e busca, letras e detalhes opcionais apenas quando o telefone puder recuperá-los.', 'Svartifoss es claro sobre lo que ofrece el reproductor activo: una cola en vivo cuando existe, historial cuando no existe y búsqueda, letras y detalles opcionales solo cuando el teléfono puede recuperarlos.');
  add('Use the player\'s queue when it exists.', 'Use a fila do player quando ela existir.', 'Usa la cola del reproductor cuando exista.');
  add('Browse the current playback queue, request more items and jump to a track. If the active player does not publish a queue, Svartifoss shows its locally tracked listening history instead.', 'Navegue pela fila atual, peça mais itens e pule para uma faixa. Se o player ativo não publicar uma fila, o Svartifoss mostra o histórico de reprodução acompanhado localmente.', 'Explora la cola actual, solicita más elementos y salta a una canción. Si el reproductor activo no publica una cola, Svartifoss muestra el historial de escucha registrado localmente.');
  add('Actual queue screen', 'Tela real da fila', 'Pantalla real de la cola');
  add('Search, library & lists', 'Busca, biblioteca e listas', 'Búsqueda, biblioteca y listas');
  add('Ask the player for what it knows.', 'Peça ao player o que ele conhece.', 'Pregunta al reproductor lo que conoce.');
  add('Search by voice or keyboard, replay recent searches, browse a MediaBrowser library when the app offers one, and keep saved playlist shortcuts reachable from the watch.', 'Busque por voz ou teclado, repita buscas recentes, navegue por uma biblioteca MediaBrowser quando o app oferecer uma e mantenha atalhos de playlists salvas acessíveis no relógio.', 'Busca por voz o teclado, repite búsquedas recientes, explora una biblioteca MediaBrowser cuando la app la ofrece y mantén accesibles desde el reloj los accesos directos a listas guardadas.');
  add('Actual list surface', 'Tela real de lista', 'Superficie real de lista');
  add('Lyrics & Metadata', 'Letras e metadados', 'Letras y metadatos');
  add('Optional, on-demand detail.', 'Detalhes opcionais, sob demanda.', 'Detalles opcionales, bajo demanda.');
  add('Lyrics are requested only when a lyrics surface or Verse needs them. Metadata is built from player tags, local-file details where permitted, and optional MusicBrainz enrichment — it never invents information the source does not provide.', 'As letras só são solicitadas quando uma superfície de letras ou o Verse precisa delas. Os metadados vêm das tags do player, de detalhes de arquivos locais quando permitido e de um enriquecimento opcional pelo MusicBrainz — nunca inventamos informações que a fonte não oferece.', 'Las letras solo se solicitan cuando una superficie de letras o Verse las necesita. Los metadatos proceden de las etiquetas del reproductor, los detalles de archivos locales cuando se permite y un enriquecimiento opcional de MusicBrainz — nunca inventa información que la fuente no proporciona.');
  add('Synced lyrics', 'Letras sincronizadas', 'Letras sincronizadas');
  add('Track tags', 'Tags da faixa', 'Etiquetas de la canción');
  add('File details', 'Detalhes do arquivo', 'Detalles del archivo');
  add('Output route', 'Rota de saída', 'Ruta de salida');
  add('Optional lookup', 'Consulta opcional', 'Consulta opcional');
  add('Streaming shortcuts', 'Atalhos de streaming', 'Accesos directos de streaming');
  add('Save a link. Let the phone do the rest.', 'Salve um link. Deixe o telefone fazer o resto.', 'Guarda un enlace. Deja que el teléfono haga el resto.');
  add('Share or paste a track, album, artist, playlist, show, episode or mix. Svartifoss first attempts the contracts the target app supports and opens the link visibly only when direct playback is unavailable.', 'Compartilhe ou cole uma faixa, álbum, artista, playlist, programa, episódio ou mix. O Svartifoss tenta primeiro os recursos que o app de destino oferece e só abre o link de forma explícita quando a reprodução direta não está disponível.', 'Comparte o pega una canción, álbum, artista, lista, programa, episodio o mezcla. Svartifoss intenta primero las funciones que admite la app de destino y solo abre el enlace de forma visible cuando la reproducción directa no está disponible.');

  add('Faces', 'Faces', 'Esferas');
  add('Twenty faces. Every one drawn from the art.', 'Vinte faces. Todas criadas a partir da arte.', 'Veinte esferas. Todas creadas a partir del arte.');
  add('These are in-app now-playing layouts, not separate Wear OS watch-face packages. Immersive, Poster, Studio, Expressive, Classic and more rebuild their palette, gradients and progress from the cover that\'s playing. Pick one per playback state and preview it live from the phone.', 'Estas são layouts de reprodução dentro do app, não pacotes separados de faces para Wear OS. Immersive, Poster, Studio, Expressive, Classic e outras recriam sua paleta, gradientes e progresso a partir da capa que está tocando. Escolha uma por estado de reprodução e visualize tudo ao vivo pelo telefone.', 'Son diseños de reproducción dentro de la app, no paquetes independientes de esferas Wear OS. Immersive, Poster, Studio, Expressive, Classic y más reconstruyen su paleta, degradados y progreso a partir de la portada que está sonando. Elige una por estado de reproducción y previsualízala en vivo desde el teléfono.');

  add('Possibilities', 'Possibilidades', 'Posibilidades');
  add('Every overlay is its own wardrobe.', 'Cada sobreposição tem seu próprio estilo.', 'Cada superposición tiene su propio estilo.');
  add('Volume, seek, the quick-actions panel and the queue each carry a full catalog of styles — a sample of each is below. Mix and match them freely; none of it is tied to whichever face is running underneath.', 'Volume, busca, painel de ações rápidas e fila têm um catálogo completo de estilos — abaixo está uma amostra de cada um. Combine livremente; nada disso fica preso à face que está sendo usada.', 'El volumen, la búsqueda, el panel de acciones rápidas y la cola tienen un catálogo completo de estilos — abajo hay una muestra de cada uno. Combínalos libremente; nada está ligado a la esfera que se esté usando.');
  add('6 of the readout styles', '6 dos estilos de leitura', '6 de los estilos de lectura');
  add('scrub & progress', 'busca e progresso', 'ajuste y progreso');
  add('double-tap actions', 'ações por toque duplo', 'acciones de doble toque');
  add('up-next & history', 'próximas faixas e histórico', 'siguiente e historial');
  add('Ring', 'Anel', 'Anillo');
  add('Dotted', 'Pontilhado', 'Punteado');
  add('Minimal', 'Minimalista', 'Minimalista');
  add('Pill bar', 'Barra em cápsula', 'Barra tipo píldora');
  add('Glass', 'Vidro', 'Cristal');
  add('Album tone', 'Tom da capa', 'Tono del álbum');
  add('Clock dial', 'Mostrador', 'Esfera de reloj');
  add('Linear', 'Linear', 'Lineal');
  add('Thin ring', 'Anel fino', 'Anillo fino');
  add('Album dots', 'Pontos da capa', 'Puntos del álbum');
  add('Round slots', 'Espaços redondos', 'Espacios redondos');
  add('Album tint', 'Matiz da capa', 'Tinte del álbum');
  add('Compact', 'Compacto', 'Compacto');
  add('State-aware', 'Sensível ao estado', 'Según el estado');
  add('Labelled rows', 'Linhas rotuladas', 'Filas etiquetadas');
  add('Album rows', 'Linhas da capa', 'Filas del álbum');
  add('Cover rows', 'Linhas com capa', 'Filas con portada');
  add('AMOLED', 'AMOLED', 'AMOLED');
  add('Tonal cards', 'Cards tonais', 'Tarjetas tonales');
  add('Photo rows', 'Linhas com foto', 'Filas con foto');
  add('Gold cards', 'Cards dourados', 'Tarjetas doradas');

  add('Depth', 'Profundidade', 'Profundidad');
  add('Not a coat of paint — every surface, really tunable.', 'Não é só uma camada de tinta — cada superfície é realmente ajustável.', 'No es solo una capa de pintura: cada superficie se puede ajustar de verdad.');
  add('Save a look as a named theme and switch between them in one tap. Each profile can combine a base face, typography, colors, artwork, background layers, progress, overlays, panels, mini buttons and the always-on display. Every gallery miniature is rendered on your phone from the theme data against a built-in sample track.', 'Salve uma aparência como tema nomeado e alterne entre eles com um toque. Cada perfil pode combinar uma face base, tipografia, cores, arte, camadas de fundo, progresso, sobreposições, painéis, mini botões e a tela sempre ativa. Cada miniatura da galeria é renderizada no telefone a partir dos dados do tema e de uma faixa de exemplo integrada.', 'Guarda un aspecto como tema con nombre y cambia entre ellos con un toque. Cada perfil puede combinar una esfera base, tipografía, colores, arte, capas de fondo, progreso, superposiciones, paneles, mini botones y pantalla siempre activa. Cada miniatura de la galería se renderiza en el teléfono con los datos del tema y una canción de muestra integrada.');
  add('Named themes, one tap to switch', 'Temas nomeados, um toque para alternar', 'Temas con nombre, un toque para cambiar');
  add('Color treatment, previewed as swatches', 'Tratamento de cor, pré-visualizado como amostras', 'Tratamiento de color, previsualizado como muestras');
  add('A dozen+ seek & volume styles', 'Mais de 12 estilos de busca e volume', 'Más de 12 estilos de búsqueda y volumen');
  add('A dozen+ queue styles', 'Mais de 12 estilos de fila', 'Más de 12 estilos de cola');
  add('Pill, circle, squircle, leaf, drop…', 'Cápsula, círculo, squircle, folha, gota…', 'Píldora, círculo, squircle, hoja, gota…');
  add('Per-face editing', 'Edição por face', 'Edición por esfera');
  add('One watch, different moods.', 'Um relógio, estados diferentes.', 'Un reloj, distintos estados.');
  add('Store appearance independently for each face instead of forcing one global look. Change title, artist, clock, lyrics and track-time typography; choose album treatments, color harmony, progress style, panel layout and custom button shapes.', 'Armazene a aparência separadamente para cada face em vez de forçar um visual global. Altere a tipografia do título, artista, relógio, letras e tempo da faixa; escolha tratamentos da capa, harmonia de cores, estilo de progresso, layout dos painéis e formas personalizadas dos botões.', 'Guarda la apariencia de cada esfera por separado en lugar de imponer un aspecto global. Cambia la tipografía del título, artista, reloj, letras y tiempo; elige tratamientos del álbum, armonía de color, estilo de progreso, diseño del panel y formas personalizadas de botones.');
  add('Always-on display', 'Tela sempre ativa', 'Pantalla siempre activa');
  add('Designed for the low-power screen too.', 'Feito também para a tela de baixo consumo.', 'Diseñada también para la pantalla de bajo consumo.');
  add('Choose an always-on presentation, artwork treatment, dim level, visibility and typography separately from the interactive player. The phone preview lets you check the ambient result before it reaches the watch.', 'Escolha uma apresentação sempre ativa, tratamento da arte, nível de brilho, visibilidade e tipografia separadamente do player interativo. A prévia no telefone permite conferir o resultado ambiente antes de enviá-lo ao relógio.', 'Elige una presentación siempre activa, tratamiento de la portada, nivel de atenuación, visibilidad y tipografía por separado del reproductor interactivo. La previsualización del teléfono permite comprobar el resultado antes de enviarlo al reloj.');
  add('Readable by design', 'Legível por design', 'Legible por diseño');
  add('Make text hold its ground.', 'Faça o texto se destacar.', 'Haz que el texto destaque.');
  add('Use album-aware or custom colors, adjustable opacity, shadow, outline and backdrop treatments for the title and artist. The same visual system also covers volume, seek, panels and queue rows.', 'Use cores baseadas na capa ou personalizadas, opacidade ajustável, sombra, contorno e fundos para o título e o artista. O mesmo sistema visual também cobre volume, busca, painéis e linhas da fila.', 'Usa colores basados en el álbum o personalizados, opacidad ajustable, sombra, contorno y fondos para el título y el artista. El mismo sistema visual también cubre volumen, búsqueda, paneles y filas de la cola.');
  add('On the phone', 'No telefone', 'En el teléfono');
  add('Preview before you send.', 'Veja antes de enviar.', 'Previsualiza antes de enviar.');
  add('The Watch tab mirrors the current track or a built-in sample and updates as you edit. Apply, duplicate, rename or delete profiles without changing the others.', 'A aba Relógio reproduz a faixa atual ou uma amostra integrada e atualiza enquanto você edita. Aplique, duplique, renomeie ou exclua perfis sem alterar os outros.', 'La pestaña Reloj refleja la canción actual o una muestra integrada y se actualiza mientras editas. Aplica, duplica, renombra o elimina perfiles sin cambiar los demás.');

  add('Community themes', 'Temas da comunidade', 'Temas de la comunidad');
  add('Borrow a look. Make it yours.', 'Pegue uma aparência. Faça dela sua.', 'Toma un estilo. Hazlo tuyo.');
  add('Browse a public catalogue of appearance profiles from the phone, inspect the full set of local previews and install only what you choose. A community theme is a starting point, not a locked preset.', 'Navegue por um catálogo público de perfis visuais no telefone, confira todas as prévias locais e instale apenas o que escolher. Um tema da comunidade é um ponto de partida, não um preset bloqueado.', 'Explora un catálogo público de perfiles visuales desde el teléfono, revisa todas las previsualizaciones locales e instala solo lo que elijas. Un tema de la comunidad es un punto de partida, no un ajuste bloqueado.');
  add('Community catalogue', 'Catálogo da comunidade', 'Catálogo de la comunidad');
  add('Made on a phone', 'Criado no telefone', 'Hecho en un teléfono');
  add('Discover a starting point.', 'Descubra um ponto de partida.', 'Descubre un punto de partida.');
  add('Search by theme or author, filter by base face, sort by newest, likes or installs, and inspect the full local previews before adding anything to your library.', 'Busque por tema ou autor, filtre pela face base, ordene pelos mais novos, curtidos ou instalados e confira todas as prévias locais antes de adicionar algo à sua biblioteca.', 'Busca por tema o autor, filtra por esfera base, ordena por novedades, favoritos o instalaciones y revisa las previsualizaciones locales antes de añadir algo a tu biblioteca.');
  add('Install, then make it yours.', 'Instale e faça do seu jeito.', 'Instala y hazlo tuyo.');
  add('Preview Player, always-on, Volume, Progress, Quick panel and Queue. Installing creates a local copy you can apply, edit, duplicate or remove independently.', 'Visualize Player, tela sempre ativa, Volume, Progresso, Painel rápido e Fila. A instalação cria uma cópia local que você pode aplicar, editar, duplicar ou remover de forma independente.', 'Previsualiza Player, pantalla siempre activa, Volumen, Progreso, Panel rápido y Cola. La instalación crea una copia local que puedes aplicar, editar, duplicar o eliminar de forma independiente.');
  add('Share your own work.', 'Compartilhe seu próprio trabalho.', 'Comparte tu trabajo.');
  add('Submit a user-owned theme for review with a public name and pseudonym or Anonymous label. Authors can optionally attach one real watch photo.', 'Envie um tema de sua autoria para análise com um nome público e pseudônimo ou o rótulo Anônimo. Os autores podem anexar opcionalmente uma foto real do relógio.', 'Envía un tema propio para revisión con un nombre público y un seudónimo o la etiqueta Anónimo. Los autores pueden adjuntar opcionalmente una foto real del reloj.');
  add('Keep public publishing accountable.', 'Mantenha a publicação pública responsável.', 'Mantén la publicación pública bajo control.');
  add('Browsing and installing need no account. Likes, installs and reports are private, while approved profiles reach the static catalogue through moderation.', 'Navegar e instalar não exige conta. Curtidas, instalações e denúncias são privadas, enquanto perfis aprovados chegam ao catálogo estático por meio de moderação.', 'Explorar e instalar no requiere cuenta. Los favoritos, instalaciones e informes son privados y los perfiles aprobados llegan al catálogo estático mediante moderación.');

  add('Compatibility & privacy', 'Compatibilidade e privacidade', 'Compatibilidad y privacidad');
  add('Built around Android\'s media standards.', 'Baseado nos padrões de mídia do Android.', 'Basado en los estándares multimedia de Android.');
  add('Svartifoss does not replace your music app, stream audio or ask for a Svartifoss cloud account. It connects the phone and watch locally, then uses the media contracts each installed player makes available.', 'O Svartifoss não substitui seu app de música, transmite áudio nem pede uma conta na nuvem do Svartifoss. Ele conecta telefone e relógio localmente e usa os contratos de mídia que cada player instalado disponibiliza.', 'Svartifoss no reemplaza tu app de música, no transmite audio ni pide una cuenta en la nube de Svartifoss. Conecta el teléfono y el reloj localmente y usa los contratos multimedia que ofrece cada reproductor instalado.');
  add('Local first', 'Local em primeiro lugar', 'Local primero');
  add('Your controls do not pass through a Svartifoss server.', 'Seus controles não passam por um servidor Svartifoss.', 'Tus controles no pasan por un servidor de Svartifoss.');
  add('The phone and watch talk over the local Wearable Data Layer. Ordinary control works without creating an account or giving a third party your listening activity.', 'O telefone e o relógio se comunicam pela Wearable Data Layer local. O controle comum funciona sem criar uma conta ou entregar sua atividade de reprodução a terceiros.', 'El teléfono y el reloj se comunican mediante la Wearable Data Layer local. El control normal funciona sin crear una cuenta ni entregar tu actividad de escucha a terceros.');
  add('Phone', 'Telefone', 'Teléfono');
  add('Media session', 'Sessão de mídia', 'Sesión multimedia');
  add('Wear OS watch', 'Relógio Wear OS', 'Reloj Wear OS');
  add('Your inputs', 'Seus comandos', 'Tus entradas');
  add('Basic playback', 'Reprodução básica', 'Reproducción básica');
  add('A usable Android MediaSession provides play, pause, skip, position and other transport commands.', 'Uma MediaSession do Android utilizável oferece reproduzir, pausar, avançar, posição e outros comandos de transporte.', 'Una MediaSession de Android funcional ofrece reproducir, pausar, saltar, posición y otros comandos de transporte.');
  add('Queue', 'Fila', 'Cola');
  add('The player must publish a queue. Otherwise Svartifoss falls back to recent track history.', 'O player precisa publicar uma fila. Caso contrário, o Svartifoss usa o histórico recente de faixas.', 'El reproductor debe publicar una cola. De lo contrario, Svartifoss usa el historial reciente de canciones.');
  add('Library & search', 'Biblioteca e busca', 'Biblioteca y búsqueda');
  add('Browsing and in-app search depend on a discoverable MediaBrowserService or compatible search command.', 'A navegação e a busca no app dependem de um MediaBrowserService detectável ou de um comando de busca compatível.', 'La navegación y la búsqueda dentro de la app dependen de un MediaBrowserService detectable o de un comando de búsqueda compatible.');
  add('Like, shuffle & repeat', 'Curtir, aleatório e repetição', 'Favorito, aleatorio y repetición');
  add('Extra actions appear when the notification or media session publishes them.', 'Ações extras aparecem quando a notificação ou a sessão de mídia as publica.', 'Las acciones adicionales aparecen cuando la notificación o la sesión multimedia las publica.');
  add('Rich metadata', 'Metadados completos', 'Metadatos enriquecidos');
  add('Tags come from the player; local file details need media access; optional MusicBrainz lookup is off by default.', 'As tags vêm do player; detalhes de arquivos locais exigem acesso à mídia; a consulta opcional ao MusicBrainz vem desativada por padrão.', 'Las etiquetas proceden del reproductor; los detalles de archivos locales necesitan acceso multimedia; la consulta opcional a MusicBrainz está desactivada por defecto.');
  add('Streaming shortcuts', 'Atalhos de streaming', 'Accesos directos de streaming');
  add('Direct playback depends on the target service. If it declines, Svartifoss opens the link in that service or a browser.', 'A reprodução direta depende do serviço de destino. Se ele recusar, o Svartifoss abre o link no serviço ou em um navegador.', 'La reproducción directa depende del servicio de destino. Si la rechaza, Svartifoss abre el enlace en ese servicio o en un navegador.');
  add('What stays local:', 'O que permanece local:', 'Lo que permanece local:');

  add('The name', 'O nome', 'El nombre');
  add('Black Falls', 'Cachoeira Negra', 'Cascada Negra');
  add('Install', 'Instalação', 'Instalación');
  add('Sideload it. Takes a minute.', 'Instale por sideload. Leva um minuto.', 'Instálala por sideload. Tarda un minuto.');
  add('Svartifoss isn\'t on the Play Store — install the two APKs directly from GitHub Releases onto your phone and watch. The phone needs Android notification access to discover the active media session; the watch app is installed separately. After the first setup, the phone can notify you about releases and send watch updates over Bluetooth.', 'O Svartifoss não está na Play Store — instale os dois APKs diretamente pelos GitHub Releases no telefone e no relógio. O telefone precisa de acesso às notificações do Android para descobrir a sessão de mídia ativa; o app do relógio é instalado separadamente. Depois da configuração inicial, o telefone pode avisar sobre lançamentos e enviar atualizações ao relógio por Bluetooth.', 'Svartifoss no está en Play Store — instala los dos APK directamente desde GitHub Releases en tu teléfono y reloj. El teléfono necesita acceso a las notificaciones de Android para descubrir la sesión multimedia activa; la app del reloj se instala por separado. Después de la configuración inicial, el teléfono puede avisarte de nuevos lanzamientos y enviar actualizaciones al reloj por Bluetooth.');
  add('Install on your phone', 'Instale no telefone', 'Instala en tu teléfono');
  add('Download the phone APK below and open it. Android may ask you to allow installs from your browser first.', 'Baixe o APK do telefone abaixo e abra-o. O Android pode pedir primeiro autorização para instalar apps pelo navegador.', 'Descarga el APK del teléfono y ábrelo. Android puede pedirte primero que permitas instalaciones desde el navegador.');
  add('Install on your watch', 'Instale no relógio', 'Instala en tu reloj');
  add('Open both apps', 'Abra os dois apps', 'Abre las dos apps');
  add('They find each other automatically over the Wearable Data Layer — no account, no pairing code, nothing else to configure.', 'Eles se encontram automaticamente pela Wearable Data Layer — sem conta, código de pareamento ou qualquer outra configuração.', 'Se encuentran automáticamente mediante la Wearable Data Layer — sin cuenta, código de emparejamiento ni nada más que configurar.');
  add('Phone · Android 6.0+', 'Telefone · Android 6.0+', 'Teléfono · Android 6.0+');
  add('Watch · Wear OS 2 or newer (3, 4, 5…)', 'Relógio · Wear OS 2 ou mais recente (3, 4, 5…)', 'Reloj · Wear OS 2 o posterior (3, 4, 5…)');
  add('License', 'Licença', 'Licencia');
  add('Privacy Policy', 'Política de privacidade', 'Política de privacidad');

  var htmlCopy = {
    'hero.badge': {
      'pt-BR': '<span class="dot">●</span> Gratuito e código aberto · somente sideload',
      es: '<span class="dot">●</span> Gratis y de código abierto · solo sideload'
    },
    'sync.title': {
      'pt-BR': 'Configure no telefone.<br>Ele aparece no relógio na hora.',
      es: 'Configúralo en el teléfono.<br>Aparece en tu reloj al instante.'
    },
    'story.description': {
      'pt-BR': '<strong>Svartifoss</strong> significa "Cachoeira Negra" em islandês — nome inspirado na cachoeira do Parque Nacional Vatnajökull, onde colunas escuras de basalto emolduram uma cascata estreita. Assim como a água sobre a pedra, a música flui pelo app.',
      es: '<strong>Svartifoss</strong> significa "Cascada Negra" en islandés — toma su nombre de la cascada del Parque Nacional Vatnajökull, donde columnas oscuras de basalto enmarcan un estrecho salto de agua. Como el agua sobre la piedra, la música fluye por la app.'
    },
    'install.watch.step': {
      'pt-BR': 'Baixe o APK do relógio e faça o sideload no seu Wear OS — o jeito mais fácil é usar <a href="https://www.xda-developers.com/wear-installer-sideload-wear-os-apps/">Wear Installer <svg class="icon"><use href="#i-open-new"/></svg></a>.',
      es: 'Descarga el APK del reloj e instálalo por sideload en tu Wear OS — la forma más fácil es usar <a href="https://www.xda-developers.com/wear-installer-sideload-wear-os-apps/">Wear Installer <svg class="icon"><use href="#i-open-new"/></svg></a>.'
    },
    'download.phone': {
      'pt-BR': '<svg class="icon"><use href="#i-send-mobile"/></svg> Baixar APK do telefone',
      es: '<svg class="icon"><use href="#i-send-mobile"/></svg> Descargar APK del teléfono'
    },
    'download.watch': {
      'pt-BR': '<svg class="icon"><use href="#i-watch-down"/></svg> Baixar APK do relógio',
      es: '<svg class="icon"><use href="#i-watch-down"/></svg> Descargar APK del reloj'
    },
    'install.source': {
      'pt-BR': 'Compilando a partir do código-fonte? Veja o <a href="https://github.com/gabrielluizone/Svartifoss#building">README</a>.',
      es: '¿Compilas desde el código fuente? Consulta el <a href="https://github.com/gabrielluizone/Svartifoss#building">README</a>.'
    },
    'compat.note': {
      'pt-BR': '<strong>O que permanece local:</strong> o controle entre telefone e relógio usa a Wearable Data Layer e não precisa de uma conta Svartifoss. Recursos de rede são opcionais ou explícitos: atualizações, catálogo de temas da comunidade, diagnósticos opcionais, avisos, letras, enriquecimento de metadados e capas de atalhos podem ser controlados nas configurações do app.',
      es: '<strong>Lo que permanece local:</strong> el control entre teléfono y reloj usa la Wearable Data Layer y no necesita una cuenta de Svartifoss. Las funciones de red son opcionales o explícitas: actualizaciones, catálogo de temas de la comunidad, diagnósticos opcionales, avisos, letras, enriquecimiento de metadatos y portadas de accesos directos se pueden controlar en los ajustes de la app.'
    },
    'footer.releases': {
      'pt-BR': 'Lançamentos <svg class="icon"><use href="#i-open-new"/></svg>',
      es: 'Versiones <svg class="icon"><use href="#i-open-new"/></svg>'
    },
    'footer.coffee': {
      'pt-BR': 'Buy Me a Coffee <svg class="icon"><use href="#i-open-new"/></svg>',
      es: 'Buy Me a Coffee <svg class="icon"><use href="#i-open-new"/></svg>'
    },
    'footer.continuation': {
      'pt-BR': 'Uma continuação do <a href="https://github.com/matejdro/WearMusicCenter">Music Center for Wear</a> de matejdro',
      es: 'Una continuación de <a href="https://github.com/matejdro/WearMusicCenter">Music Center for Wear</a> de matejdro'
    }
  };

  var metadata = {
    en: {
      title: 'Svartifoss — control your music from your wrist',
      description: 'Svartifoss is a free, open-source Wear OS companion that puts playback, search, queue, lyrics, metadata and deeply customizable media controls on your wrist.'
    },
    'pt-BR': {
      title: 'Svartifoss — controle sua música pelo pulso',
      description: 'Svartifoss é um companheiro gratuito e de código aberto para Wear OS que coloca reprodução, busca, filas, letras, metadados e controles de mídia profundamente personalizáveis no seu pulso.'
    },
    es: {
      title: 'Svartifoss — controla tu música desde la muñeca',
      description: 'Svartifoss es un compañero gratuito y de código abierto para Wear OS que lleva reproducción, búsqueda, colas, letras, metadatos y controles multimedia profundamente personalizables a tu muñeca.'
    }
  };

  var selector = document.getElementById('language-select');
  var supported = { en: true, 'pt-BR': true, es: true };
  var language = 'en';

  try {
    var stored = window.localStorage.getItem('svartifoss-language');
    if (stored && supported[stored]) language = stored;
  } catch (ignore) {}

  function translatePage(nextLanguage) {
    language = supported[nextLanguage] ? nextLanguage : 'en';

    document.documentElement.lang = language;
    if (selector) {
      selector.value = language;
      selector.setAttribute('aria-label', language === 'en' ? 'Language' : 'Idioma');
    }

    document.querySelectorAll('[data-i18n-html]').forEach(function (element) {
      var key = element.getAttribute('data-i18n-html');
      var source = element.getAttribute('data-i18n-html-source');
      if (source === null) {
        source = element.innerHTML;
        element.setAttribute('data-i18n-html-source', source);
      }
      var translated = htmlCopy[key] && htmlCopy[key][language];
      element.innerHTML = language === 'en' ? source : (translated || source);
    });

    document.querySelectorAll('body *').forEach(function (element) {
      if (element.children.length || element.closest('[data-i18n-html]') || element.closest('svg')) return;
      if (element.matches('script, style, svg, use, select, option, img')) return;

      var source = element.getAttribute('data-i18n-source');
      if (source === null) {
        source = element.getAttribute('data-i18n') || element.textContent.trim();
        element.setAttribute('data-i18n-source', source);
      }
      if (!source) return;

      var translated = textCopy[source] && textCopy[source][language];
      element.textContent = language === 'en' ? source : (translated || source);
    });

    var pageMeta = metadata[language];
    document.title = pageMeta.title;
    var description = document.querySelector('meta[name="description"]');
    if (description) description.setAttribute('content', pageMeta.description);

    var closeButton = document.querySelector('.lightbox-close');
    if (closeButton) closeButton.setAttribute('aria-label', language === 'en' ? 'Close enlarged image' : (language === 'pt-BR' ? 'Fechar imagem ampliada' : 'Cerrar imagen ampliada'));

    try { window.localStorage.setItem('svartifoss-language', language); } catch (ignore) {}
  }

  if (selector) selector.addEventListener('change', function () { translatePage(selector.value); });
  translatePage(language);
})();
