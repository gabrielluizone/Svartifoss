(function () {
  var textCopy = {};

  function add(source, portuguese, spanish, icelandic) {
    textCopy[source] = { 'pt-BR': portuguese, es: spanish, is: icelandic };
  }

  add('Language', 'Idioma', 'Idioma', 'Tungumál');
  add('Features', 'Recursos', 'Funciones', 'Eiginleikar');
  add('Control', 'Controle', 'Controles', 'Stjórnun');
  add('Content', 'Conteúdo', 'Contenido', 'Efni');
  add('Look & feel', 'Aparência', 'Aspecto', 'Útlit');
  add('Possibilities', 'Possibilidades', 'Posibilidades', 'Möguleikar');
  add('Customize', 'Personalizar', 'Personalizar', 'Sérsníða');
  add('Compatibility', 'Compatibilidade', 'Compatibilidad', 'Samhæfni');
  add('Install', 'Instalação', 'Instalación', 'Uppsetning');
  add('Download', 'Baixar', 'Descargar', 'Sækja');
  add('The most customizable media controls on Wear OS', 'Os controles de mídia mais personalizáveis para Wear OS', 'Los controles multimedia más personalizables para Wear OS', 'Sérsniðnasta miðlunarstýringin fyrir Wear OS');
  add('Svartifoss reads whatever\'s playing on your phone — music, podcasts, audiobooks or any other media app — and puts playback, search, queues, lyrics and metadata on your wrist. Every button, gesture, screen and visual layer can be shaped around the way you use your watch.', 'O Svartifoss lê o que estiver sendo reproduzido no telefone — músicas, podcasts, audiolivros ou qualquer outro app de mídia — e coloca reprodução, busca, filas, letras e metadados no seu pulso. Cada botão, gesto, tela e camada visual pode ser adaptado à forma como você usa o relógio.', 'Svartifoss lee lo que se está reproduciendo en tu teléfono — música, podcasts, audiolibros o cualquier otra app multimedia — y lleva la reproducción, búsqueda, colas, letras y metadatos a tu muñeca. Cada botón, gesto, pantalla y capa visual puede adaptarse a la forma en que usas tu reloj.', 'Svartifoss les hvað sem er í spilun í símanum þínum — tónlist, hlaðvörp, hljóðbækur eða hvaða annað miðlunarforrit sem er — og færir afspilun, leit, biðraðir, texta og lýsigögn á úlnliðinn. Sérhver hnappur, bending, skjár og myndlag er hægt að móta eftir því hvernig þú notar úrið þitt.');
  add('Download Svartifoss', 'Baixar Svartifoss', 'Descargar Svartifoss', 'Sækja Svartifoss');
  add('View source on GitHub', 'Ver código-fonte no GitHub', 'Ver código fuente en GitHub', 'Skoða frumkóða á GitHub');
  add('Downloads', 'Downloads', 'Descargas', 'Niðurhal');
  add('Latest version', 'Versão mais recente', 'Última versión', 'Nýjasta útgáfa');
  add('GitHub stars', 'Estrelas no GitHub', 'Estrellas en GitHub', 'GitHub-stjörnur');
  add('Watch faces', 'Faces do relógio', 'Esferas del reloj', 'Úraskífur');

  add('How it works', 'Como funciona', 'Cómo funciona', 'Hvernig þetta virkar');
  add('Every button, gesture, and screen is yours to assign from the phone app. The watch mirrors it over the local Wearable Data Layer — no account or Svartifoss server. Beyond your two devices, the app talks to GitHub for the optional update check and only when you explicitly open the Community themes gallery, to Firebase for diagnostics and occasional developer announcement notifications, and — only if you opt in — to the streaming service itself to fetch a saved shortcut\'s cover art. Crashlytics reports, announcement notifications, and shortcut artwork can all be switched off under Data & support → Privacy or Apps.', 'Você pode atribuir cada botão, gesto e tela pelo app do telefone. O relógio recebe tudo pela Wearable Data Layer local — sem conta ou servidor do Svartifoss. Além dos seus dois dispositivos, o app acessa o GitHub para verificar atualizações opcionalmente e somente quando você abre a galeria de temas da comunidade, o Firebase para diagnósticos e avisos ocasionais do desenvolvedor e — apenas se você permitir — o próprio serviço de streaming para buscar a capa de um atalho salvo. Relatórios do Crashlytics, avisos e capas dos atalhos podem ser desativados em Dados e suporte → Privacidade ou Apps.', 'Puedes asignar cada botón, gesto y pantalla desde la app del teléfono. El reloj lo refleja mediante la Wearable Data Layer local — sin cuenta ni servidor de Svartifoss. Fuera de tus dos dispositivos, la app contacta con GitHub para comprobar actualizaciones opcionales y solo cuando abres la galería de temas de la comunidad, con Firebase para diagnósticos y avisos ocasionales del desarrollador y — solo si lo permites — con el propio servicio de streaming para obtener la portada de un acceso directo guardado. Los informes de Crashlytics, avisos y portadas de accesos directos se pueden desactivar en Datos y soporte → Privacidad o Apps.', 'Þú getur úthlutað sérhverjum hnappi, bendingu og skjá úr símaforritinu. Úrið endurspeglar það um staðbundið Wearable Data Layer — enginn notandaaðgangur né Svartifoss-netþjónn. Fyrir utan tækin þín tvö hefur appið samband við GitHub til að athuga valfrjálst hvort ný útgáfa sé til og aðeins þegar þú opnar sérstaklega safn samfélagsþema, við Firebase vegna villugreiningar og stöku tilkynninga frá þróunaraðila, og — aðeins ef þú velur það sjálf(ur) — við streymisþjónustuna sjálfa til að sækja forsíðumynd vistaðrar flýtileiðar. Hægt er að slökkva á Crashlytics-skýrslum, tilkynningum og myndum flýtileiða undir Gögn og aðstoð → Persónuvernd eða Forrit.');
  add('Assign on the phone', 'Configure no telefone', 'Configurar en el teléfono', 'Úthlutað í símanum');
  add('Live on the watch', 'Ao vivo no relógio', 'En vivo en el reloj', 'Í beinni á úrinu');

  add('What you get', 'O que você recebe', 'Qué obtienes', 'Það sem þú færð');
  add('Nothing about Svartifoss is fixed. Pick what each input does, how the screen looks, and what shows up when nothing\'s bound to a button.', 'Nada no Svartifoss é fixo. Escolha o que cada entrada faz, como a tela aparece e o que é mostrado quando nada está vinculado a um botão.', 'Nada en Svartifoss es fijo. Elige qué hace cada entrada, cómo se ve la pantalla y qué aparece cuando nada está asignado a un botón.', 'Ekkert í Svartifoss er fastákveðið. Veldu hvað hver innsláttur gerir, hvernig skjárinn lítur út og hvað birtist þegar ekkert er tengt við hnapp.');
  add('Every surface, configurable.', 'Todas as superfícies, configuráveis.', 'Cada superficie, configurable.', 'Hvert yfirborð, sérsniðanlegt.');
  add('Now-playing screen', 'Tela de reprodução', 'Pantalla de reproducción', 'Núspilunarskjár');
  add('Album art, live position, transport controls and a draggable seek surface. Choose from fifteen in-app faces, including Classic, Expressive, Poster, Studio, Carousel, Verse, Metadata and the performer-forward Artist — each one rebuilt around the cover that\'s playing.', 'Capa do álbum, posição ao vivo, controles de reprodução e uma superfície de busca arrastável. Escolha entre quinze faces no app, incluindo Classic, Expressive, Poster, Studio, Carousel, Verse, Metadata e a Artist, voltada para o artista — cada uma reconstruída em torno da capa que está tocando.', 'Portada del álbum, posición en vivo, controles de reproducción y una superficie de búsqueda arrastrable. Elige entre quince esferas dentro de la app, como Classic, Expressive, Poster, Studio, Carousel, Verse, Metadata y la Artist, centrada en el artista — cada una reconstruida alrededor de la portada que está sonando.', 'Plötuumslag, staðsetning í beinni, spilunarstýringar og leitaryfirborð sem hægt er að draga til. Veldu úr fimmtán skífum inni í appinu, þar á meðal Classic, Expressive, Poster, Studio, Carousel, Verse, Metadata og flytjendamiðuðu Artist-skífunni — hver þeirra endurgerð út frá umslaginu sem er í spilun.');
  add('Configurable input', 'Entrada configurável', 'Entrada configurable', 'Stillanleg inntök');
  add('Physical buttons, screen quadrants, swipes, the center tap, digital crown, bezel, mini buttons and supported double-pinch gestures can all run different actions while music is playing or stopped.', 'Botões físicos, quadrantes da tela, gestos de deslizar, toque no centro, coroa digital, borda, mini botões e gestos de pinça dupla compatíveis podem executar ações diferentes com a música tocando ou parada.', 'Los botones físicos, cuadrantes de pantalla, deslizamientos, toque central, corona digital, bisel, mini botones y gestos de doble pellizco compatibles pueden ejecutar acciones distintas mientras la música suena o está detenida.', 'Efnislegir hnappar, skjáfjórðungar, strokur, miðjusnerting, stafræn kóróna, jaðarhringur, míníhnappar og studdar tvíklípubendingar geta allar keyrt mismunandi aðgerðir eftir því hvort tónlist er í spilun eða stöðvuð.');
  add('Quick-actions panel', 'Painel de ações rápidas', 'Panel de acciones rápidas', 'Flýtiaðgerðaspjald');
  add('Double-tap to open a configurable panel with three round slots and one wide row. Pick an action for every slot, or mirror the real actions and icons published by the current player\'s notification.', 'Toque duas vezes para abrir um painel configurável com três espaços redondos e uma linha larga. Escolha uma ação para cada espaço ou use as ações e os ícones publicados pela notificação do player atual.', 'Toca dos veces para abrir un panel configurable con tres espacios redondos y una fila ancha. Elige una acción para cada espacio o refleja las acciones e iconos publicados por la notificación del reproductor actual.', 'Tvísmelltu til að opna stillanlegt spjald með þremur hringlaga reitum og einni breiðri röð. Veldu aðgerð fyrir hvern reit, eða láttu spjaldið endurspegla raunverulegar aðgerðir og táknmyndir sem tilkynning núverandi spilara birtir.');
  add('Queue & history', 'Fila e histórico', 'Cola e historial', 'Biðröð og saga');
  add('Browse a live queue with paging and artwork when the player exposes one, jump to an exact item, or fall back honestly to locally tracked listening history when it doesn\'t.', 'Navegue por uma fila ao vivo com paginação e capas quando o player oferecer uma, pule para um item específico ou use honestamente o histórico de reprodução acompanhado localmente quando ele não oferecer uma fila.', 'Explora una cola en vivo con páginas y portadas cuando el reproductor la expone, salta a un elemento concreto o usa el historial de escucha local cuando no existe una cola.', 'Skoðaðu biðröð í beinni með síðuskiptingu og umslagsmyndum þegar spilarinn býður upp á hana, hoppaðu beint á tiltekið lag, eða reiddu þig heiðarlega á staðbundna hlustunarsögu þegar engin biðröð er í boði.');
  add('Glanceable surfaces', 'Superfícies rápidas', 'Superficies de vistazo', 'Yfirlitsskjáir');
  add('A media Tile with transport and ±10-second seek, a second Tile for saved streaming shortcuts, and an album-art/title complication for the watch face you\'re already using.', 'Um Tile de mídia com controles e busca de ±10 segundos, um segundo Tile para atalhos de streaming salvos e uma complicação de capa/título para a face que você já usa.', 'Un Tile multimedia con controles y búsqueda de ±10 segundos, un segundo Tile para accesos directos de streaming guardados y una complicación de portada/título para la esfera que ya usas.', 'Miðlunar-Tile með spilunarstýringum og ±10 sekúndna leit, önnur Tile fyrir vistaðar streymisflýtileiðir, og fylgihlutur (complication) með umslagsmynd/titli fyrir úraskífuna sem þú notar nú þegar.');
  add('Search & playlists', 'Busca e playlists', 'Búsqueda y listas', 'Leit og lagalistar');
  add('Search by voice or keyboard, browse the player\'s library, replay or remove search history, and open saved links to tracks, albums, playlists, artists, shows and mixes.', 'Busque por voz ou teclado, navegue pela biblioteca do player, repita ou remova o histórico de buscas e abra links salvos de faixas, álbuns, playlists, artistas, programas e mixes.', 'Busca por voz o teclado, explora la biblioteca del reproductor, repite o elimina el historial de búsqueda y abre enlaces guardados de canciones, álbumes, listas, artistas, programas y mezclas.', 'Leitaðu með rödd eða lyklaborði, skoðaðu safn spilarans, spilaðu aftur eða eyddu leitarsögu, og opnaðu vistaða tengla á lög, plötur, lagalista, flytjendur, þætti og blöndur.');
  add('Full action menu', 'Menu completo de ações', 'Menú completo de acciones', 'Fullur aðgerðalisti');
  add('A full-screen list for anything not bound to a button or gesture — nothing is ever more than one extra tap away.', 'Uma lista em tela cheia para tudo que não estiver vinculado a um botão ou gesto — nada fica a mais de um toque extra.', 'Una lista a pantalla completa para todo lo que no esté asignado a un botón o gesto — nada queda a más de un toque adicional.', 'Heilskjáslisti fyrir allt sem er ekki tengt hnappi eða bendingu — ekkert er nokkurn tímann meira en eina aukasnertingu í burtu.');
  add('Works with any player', 'Funciona com qualquer player', 'Funciona con cualquier reproductor', 'Virkar með hvaða spilara sem er');
  add('Any app exposing a standard Android media session can provide basic control. Queues, libraries, search, likes and other extras appear when that player publishes the relevant Android capability.', 'Qualquer app que exponha uma sessão de mídia padrão do Android pode oferecer controles básicos. Filas, bibliotecas, buscas, curtidas e outros extras aparecem quando o player publica o recurso correspondente do Android.', 'Cualquier app que exponga una sesión multimedia estándar de Android puede ofrecer controles básicos. Las colas, bibliotecas, búsquedas, favoritos y otros extras aparecen cuando el reproductor publica la capacidad correspondiente de Android.', 'Hvaða forrit sem býður upp á staðlaða Android-miðlunarlotu (media session) getur veitt grunnstjórnun. Biðraðir, söfn, leit, „líkar við" og önnur aukaatriði birtast þegar spilarinn birtir viðeigandi Android-eiginleika.');

  add('Real player surfaces', 'Superfícies reais do player', 'Superficies reales del reproductor', 'Raunveruleg yfirborð spilarans');
  add('Tap any image to enlarge', 'Toque em qualquer imagem para ampliar', 'Toca cualquier imagen para ampliar', 'Ýttu á mynd til að stækka hana');
  add('Every action, on your terms.', 'Cada ação, do seu jeito.', 'Cada acción, a tu manera.', 'Sérhver aðgerð, á þínum forsendum.');
  add('Mini buttons', 'Mini botões', 'Mini botones', 'Míníhnappar');
  add('Choose each action', 'Escolha cada ação', 'Elige cada acción', 'Veldu hverja aðgerð');
  add('Real player surface', 'Superfície real do player', 'Superficie real del reproductor', 'Raunverulegt yfirborð spilarans');
  add('Edge seek', 'Busca pela borda', 'Búsqueda en el bisel', 'Jaðarleit');
  add('Drag around the bezel', 'Arraste ao redor da borda', 'Arrastra alrededor del bisel', 'Dragðu meðfram jaðrinum');
  add('Quick actions', 'Ações rápidas', 'Acciones rápidas', 'Flýtiaðgerðir');
  add('Double-tap from the player', 'Toque duas vezes no player', 'Toca dos veces desde el reproductor', 'Tvísmelltu úr spilaranum');
  add('Full player', 'Player completo', 'Reproductor completo', 'Allur spilarinn');
  add('The essentials in view', 'O essencial à vista', 'Lo esencial a la vista', 'Grundvallaratriðin í sjónmáli');
  add('Playback', 'Reprodução', 'Reproducción', 'Afspilun');
  add('More than play and pause.', 'Mais que reproduzir e pausar.', 'Mucho más que reproducir y pausar.', 'Meira en að spila og gera hlé.');
  add('Skip, restart, stop, seek by percentage, jump forward or back, change playback speed, choose shuffle and repeat modes, or run a player-specific like action.', 'Avance, reinicie, pare, busque por porcentagem, pule para frente ou para trás, altere a velocidade, escolha os modos aleatório e repetição ou execute a ação de curtir específica do player.', 'Avanza, reinicia, detén, busca por porcentaje, salta hacia delante o atrás, cambia la velocidad, elige los modos aleatorio y repetición o ejecuta la acción de favorito del reproductor.', 'Sleptu áfram, endurræstu, stöðvaðu, leitaðu eftir prósentu, hoppaðu fram eða til baka, breyttu spilunarhraða, veldu slembi- og endurtekningarstillingar, eða keyrðu „líkar við"-aðgerð sem er sértæk fyrir spilarann.');
  add('Inputs', 'Entradas', 'Entradas', 'Inntök');
  add('Use the controls your watch actually has.', 'Use os controles que seu relógio realmente tem.', 'Usa los controles que realmente tiene tu reloj.', 'Notaðu þær stýringar sem úrið þitt hefur í raun og veru.');
  add('Physical buttons, single/double/long presses, touch zones, swipes, center actions, mini buttons and — on compatible watches — crown, bezel or double-pinch input.', 'Botões físicos, toques simples/duplos/longos, zonas de toque, deslizes, ações no centro, mini botões e — em relógios compatíveis — entradas pela coroa, borda ou pinça dupla.', 'Botones físicos, pulsaciones simples/dobles/largas, zonas táctiles, deslizamientos, acciones centrales, mini botones y — en relojes compatibles — entradas mediante corona, bisel o doble pellizco.', 'Efnislegir hnappar, einn/tvö-/langsmell, snertisvæði, strokur, miðjuaðgerðir, míníhnappar og — á samhæfðum úrum — kóróna, jaðarhringur eða tvíklípuinntak.');
  add('States', 'Estados', 'Estados', 'Stöður');
  add('One setup while playing. Another when idle.', 'Uma configuração durante a reprodução. Outra quando está parado.', 'Una configuración mientras reproduce. Otra cuando está detenido.', 'Ein uppsetning meðan spilun stendur yfir. Önnur þegar ekkert er í gangi.');
  add('Music playing and No playback are separate configurations. The same input can skip a track now and open a saved playlist when playback has stopped.', 'Música tocando e Sem reprodução são configurações separadas. A mesma entrada pode pular uma faixa agora e abrir uma playlist salva quando a reprodução parar.', 'Música reproduciéndose y Sin reproducción son configuraciones separadas. La misma entrada puede saltar una canción ahora y abrir una lista guardada cuando la reproducción se detenga.', '„Tónlist í spilun" og „Engin spilun" eru aðskildar stillingar. Sama inntak getur sleppt lagi núna og opnað vistaðan lagalista þegar spilun hefur stöðvast.');
  add('Action menu', 'Menu de ações', 'Menú de acciones', 'Aðgerðalisti');
  add('Keep the rest one tap away.', 'Deixe o restante a um toque.', 'Deja el resto a un toque.', 'Hafðu allt annað eina snertingu í burtu.');
  add('Open queue, lyrics, volume, progress or face picker; browse a library; launch an app; run Tasker; or start a streaming shortcut without reserving a physical button.', 'Abra a fila, letras, volume, progresso ou seletor de face; navegue por uma biblioteca; inicie um app; execute o Tasker; ou abra um atalho de streaming sem ocupar um botão físico.', 'Abre la cola, letras, volumen, progreso o selector de esfera; explora una biblioteca; inicia una app; ejecuta Tasker; o abre un acceso directo de streaming sin reservar un botón físico.', 'Opnaðu biðröð, texta, hljóðstyrk, framvindu eða skífuval; skoðaðu safn; opnaðu forrit; keyrðu Tasker; eða ræstu streymisflýtileið án þess að taka frá efnislegan hnapp.');

  add('Content on your wrist', 'Conteúdo no seu pulso', 'Contenido en tu muñeca', 'Efni á úlnliðnum');
  add('More than a now-playing screen.', 'Mais que uma tela de reprodução.', 'Más que una pantalla de reproducción.', 'Meira en núspilunarskjár.');
  add('Svartifoss stays honest about what the active player makes available: a live queue when there is one, history when there is not, and optional search, lyrics and details only where the phone can retrieve them.', 'O Svartifoss é transparente sobre o que o player ativo oferece: uma fila ao vivo quando existir, histórico quando não existir e busca, letras e detalhes opcionais apenas quando o telefone puder recuperá-los.', 'Svartifoss es claro sobre lo que ofrece el reproductor activo: una cola en vivo cuando existe, historial cuando no existe y búsqueda, letras y detalles opcionales solo cuando el teléfono puede recuperarlos.', 'Svartifoss er alltaf heiðarlegt um hvað virki spilarinn hefur upp á að bjóða: biðröð í beinni þegar hún er til staðar, sögu þegar hún er það ekki, og valfrjálsa leit, texta og nánari upplýsingar aðeins þar sem síminn getur nálgast þær.');
  add('Use the player\'s queue when it exists.', 'Use a fila do player quando ela existir.', 'Usa la cola del reproductor cuando exista.', 'Notaðu biðröð spilarans þegar hún er til.');
  add('Browse the current playback queue, request more items and jump to a track. If the active player does not publish a queue, Svartifoss shows its locally tracked listening history instead.', 'Navegue pela fila atual, peça mais itens e pule para uma faixa. Se o player ativo não publicar uma fila, o Svartifoss mostra o histórico de reprodução acompanhado localmente.', 'Explora la cola actual, solicita más elementos y salta a una canción. Si el reproductor activo no publica una cola, Svartifoss muestra el historial de escucha registrado localmente.', 'Skoðaðu núverandi biðröð, biddu um fleiri atriði og hoppaðu á tiltekið lag. Ef virki spilarinn birtir enga biðröð sýnir Svartifoss staðbundna hlustunarsögu í staðinn.');
  add('Actual queue screen', 'Tela real da fila', 'Pantalla real de la cola', 'Raunverulegur biðraðarskjár');
  add('Search, library & lists', 'Busca, biblioteca e listas', 'Búsqueda, biblioteca y listas', 'Leit, safn og listar');
  add('Ask the player for what it knows.', 'Peça ao player o que ele conhece.', 'Pregunta al reproductor lo que conoce.', 'Spurðu spilarann hvað hann veit.');
  add('Search by voice or keyboard, replay recent searches, browse a MediaBrowser library when the app offers one, and keep saved playlist shortcuts reachable from the watch.', 'Busque por voz ou teclado, repita buscas recentes, navegue por uma biblioteca MediaBrowser quando o app oferecer uma e mantenha atalhos de playlists salvas acessíveis no relógio.', 'Busca por voz o teclado, repite búsquedas recientes, explora una biblioteca MediaBrowser cuando la app la ofrece y mantén accesibles desde el reloj los accesos directos a listas guardadas.', 'Leitaðu með rödd eða lyklaborði, skoðaðu nýlegar leitir aftur, flettu í gegnum MediaBrowser-safn þegar forritið býður upp á það, og hafðu vistaðar lagalistaflýtileiðir aðgengilegar úr úrinu.');
  add('Actual list surface', 'Tela real de lista', 'Superficie real de lista', 'Raunverulegt listayfirborð');
  add('Lyrics & Metadata', 'Letras e metadados', 'Letras y metadatos', 'Textar og lýsigögn');
  add('Optional, on-demand detail.', 'Detalhes opcionais, sob demanda.', 'Detalles opcionales, bajo demanda.', 'Valfrjálsar upplýsingar, sóttar eftir þörfum.');
  add('Lyrics are requested only when a lyrics surface or Verse needs them. Metadata is built from player tags, local-file details where permitted, and optional MusicBrainz enrichment — it never invents information the source does not provide.', 'As letras só são solicitadas quando uma superfície de letras ou o Verse precisa delas. Os metadados vêm das tags do player, de detalhes de arquivos locais quando permitido e de um enriquecimento opcional pelo MusicBrainz — nunca inventamos informações que a fonte não oferece.', 'Las letras solo se solicitan cuando una superficie de letras o Verse las necesita. Los metadatos proceden de las etiquetas del reproductor, los detalles de archivos locales cuando se permite y un enriquecimiento opcional de MusicBrainz — nunca inventa información que la fuente no proporciona.', 'Textar eru einungis sóttir þegar textayfirborð eða Verse þarfnast þeirra. Lýsigögn eru byggð úr merkjum spilarans, upplýsingum úr staðbundnum skrám þar sem leyfi er fyrir hendi, og valfrjálsri auðgun frá MusicBrainz — aldrei er upplýsingum sem heimildin veitir ekki fundið upp.');
  add('Synced lyrics', 'Letras sincronizadas', 'Letras sincronizadas', 'Samstilltir textar');
  add('Track tags', 'Tags da faixa', 'Etiquetas de la canción', 'Merki lagsins');
  add('File details', 'Detalhes do arquivo', 'Detalles del archivo', 'Upplýsingar um skrá');
  add('Output route', 'Rota de saída', 'Ruta de salida', 'Úttaksleið');
  add('Optional lookup', 'Consulta opcional', 'Consulta opcional', 'Valfrjáls uppfletting');
  add('Streaming shortcuts', 'Atalhos de streaming', 'Accesos directos de streaming', 'Streymisflýtileiðir');
  add('Save a link. Let the phone do the rest.', 'Salve um link. Deixe o telefone fazer o resto.', 'Guarda un enlace. Deja que el teléfono haga el resto.', 'Vistaðu tengil. Láttu símann sjá um restina.');
  add('Share or paste a track, album, artist, playlist, show, episode or mix. Svartifoss first attempts the contracts the target app supports and opens the link visibly only when direct playback is unavailable.', 'Compartilhe ou cole uma faixa, álbum, artista, playlist, programa, episódio ou mix. O Svartifoss tenta primeiro os recursos que o app de destino oferece e só abre o link de forma explícita quando a reprodução direta não está disponível.', 'Comparte o pega una canción, álbum, artista, lista, programa, episodio o mezcla. Svartifoss intenta primero las funciones que admite la app de destino y solo abre el enlace de forma visible cuando la reproducción directa no está disponible.', 'Deildu eða límdu inn tengil á lag, plötu, flytjanda, lagalista, þátt, þáttahluta eða blöndu. Svartifoss reynir fyrst þær leiðir sem markforritið styður og opnar tengilinn sýnilega aðeins þegar bein afspilun er ekki í boði.');

  add('Faces', 'Faces', 'Esferas', 'Skífur');
  add('Fifteen faces. Every one drawn from the art.', 'Quinze faces. Todas criadas a partir da arte.', 'Quince esferas. Todas creadas a partir del arte.', 'Fimmtán skífur. Allar mótaðar út frá myndverkinu.');
  add('These are in-app now-playing layouts, not separate Wear OS watch-face packages. Immersive, Poster, Studio, Expressive, Classic and more rebuild their palette, gradients and progress from the cover that\'s playing. Pick one per playback state and preview it live from the phone.', 'Estas são layouts de reprodução dentro do app, não pacotes separados de faces para Wear OS. Immersive, Poster, Studio, Expressive, Classic e outras recriam sua paleta, gradientes e progresso a partir da capa que está tocando. Escolha uma por estado de reprodução e visualize tudo ao vivo pelo telefone.', 'Son diseños de reproducción dentro de la app, no paquetes independientes de esferas Wear OS. Immersive, Poster, Studio, Expressive, Classic y más reconstruyen su paleta, degradados y progreso a partir de la portada que está sonando. Elige una por estado de reproducción y previsualízala en vivo desde el teléfono.', 'Þetta eru núspilunarútlit innan appsins sjálfs, ekki sérstakir Wear OS úraskífupakkar. Immersive, Poster, Studio, Expressive, Classic og fleiri endurbyggja litatóna, litstigla og framvindu út frá umslaginu sem er í spilun. Veldu eina fyrir hvora spilunarstöðu og forskoðaðu hana í beinni úr símanum.');

  add('Possibilities', 'Possibilidades', 'Posibilidades', 'Möguleikar');
  add('Every overlay is its own wardrobe.', 'Cada sobreposição tem seu próprio estilo.', 'Cada superposición tiene su propio estilo.', 'Hvert yfirlag hefur sinn eigin fataskáp.');
  add('Volume, seek, the quick-actions panel and the queue each carry a full catalog of styles — a sample of each is below. Mix and match them freely; none of it is tied to whichever face is running underneath.', 'Volume, busca, painel de ações rápidas e fila têm um catálogo completo de estilos — abaixo está uma amostra de cada um. Combine livremente; nada disso fica preso à face que está sendo usada.', 'El volumen, la búsqueda, el panel de acciones rápidas y la cola tienen un catálogo completo de estilos — abajo hay una muestra de cada uno. Combínalos libremente; nada está ligado a la esfera que se esté usando.', 'Hljóðstyrkur, leit, flýtiaðgerðaspjaldið og biðröðin bera hvert um sig fullt safn af stílum — sýnishorn af hverjum er hér fyrir neðan. Blandaðu þeim saman að vild; ekkert af þessu er bundið því hvaða skífa er í gangi undir niðri.');
  add('6 of the readout styles', '6 dos estilos de leitura', '6 de los estilos de lectura', '6 af aflestrarstílunum');
  add('scrub & progress', 'busca e progresso', 'ajuste y progreso', 'spólun og framvinda');
  add('double-tap actions', 'ações por toque duplo', 'acciones de doble toque', 'tvísmellsaðgerðir');
  add('up-next & history', 'próximas faixas e histórico', 'siguiente e historial', 'næst á dagskrá og saga');
  add('Ring', 'Anel', 'Anillo', 'Hringur');
  add('Dotted', 'Pontilhado', 'Punteado', 'Punktað');
  add('Minimal', 'Minimalista', 'Minimalista', 'Naumhyggja');
  add('Pill bar', 'Barra em cápsula', 'Barra tipo píldora', 'Hylkisstika');
  add('Glass', 'Vidro', 'Cristal', 'Gler');
  add('Album tone', 'Tom da capa', 'Tono del álbum', 'Tónn úr plötu');
  add('Clock dial', 'Mostrador', 'Esfera de reloj', 'Klukkuskífa');
  add('Linear', 'Linear', 'Lineal', 'Línulegt');
  add('Thin ring', 'Anel fino', 'Anillo fino', 'Mjór hringur');
  add('Album dots', 'Pontos da capa', 'Puntos del álbum', 'Punktar úr plötu');
  add('Round slots', 'Espaços redondos', 'Espacios redondos', 'Hringlaga reitir');
  add('Album tint', 'Matiz da capa', 'Tinte del álbum', 'Blær úr plötu');
  add('Compact', 'Compacto', 'Compacto', 'Þjappað');
  add('State-aware', 'Sensível ao estado', 'Según el estado', 'Eftir stöðu');
  add('Labelled rows', 'Linhas rotuladas', 'Filas etiquetadas', 'Merktar raðir');
  add('Album rows', 'Linhas da capa', 'Filas del álbum', 'Raðir með plötu');
  add('Cover rows', 'Linhas com capa', 'Filas con portada', 'Raðir með umslagi');
  add('AMOLED', 'AMOLED', 'AMOLED', 'AMOLED');
  add('Tonal cards', 'Cards tonais', 'Tarjetas tonales', 'Tónuð spjöld');
  add('Photo rows', 'Linhas com foto', 'Filas con foto', 'Raðir með ljósmynd');
  add('Gold cards', 'Cards dourados', 'Tarjetas doradas', 'Gullslegin spjöld');

  add('Depth', 'Profundidade', 'Profundidad', 'Dýpt');
  add('Not a coat of paint — every surface, really tunable.', 'Não é só uma camada de tinta — cada superfície é realmente ajustável.', 'No es solo una capa de pintura: cada superficie se puede ajustar de verdad.', 'Ekki bara málningarlag — hvert yfirborð er sannarlega stillanlegt.');
  add('Save a look as a named theme and switch between them in one tap. Each profile can combine a base face, typography, colors, artwork, background layers, progress, overlays, panels, mini buttons and the always-on display. Every gallery miniature is rendered on your phone from the theme data against a built-in sample track.', 'Salve uma aparência como tema nomeado e alterne entre eles com um toque. Cada perfil pode combinar uma face base, tipografia, cores, arte, camadas de fundo, progresso, sobreposições, painéis, mini botões e a tela sempre ativa. Cada miniatura da galeria é renderizada no telefone a partir dos dados do tema e de uma faixa de exemplo integrada.', 'Guarda un aspecto como tema con nombre y cambia entre ellos con un toque. Cada perfil puede combinar una esfera base, tipografía, colores, arte, capas de fondo, progreso, superposiciones, paneles, mini botones y pantalla siempre activa. Cada miniatura de la galería se renderiza en el teléfono con los datos del tema y una canción de muestra integrada.', 'Vistaðu útlit sem nefnt þema og skiptu á milli þeirra með einni snertingu. Hver stilling getur sameinað grunnskífu, leturgerð, liti, myndverk, bakgrunnslög, framvindu, yfirlög, spjöld, míníhnappa og sískjáinn. Sérhver smámynd í safninu er teiknuð í símanum þínum út frá þemagögnunum og innbyggðu sýnishornslagi.');
  add('Named themes, one tap to switch', 'Temas nomeados, um toque para alternar', 'Temas con nombre, un toque para cambiar', 'Nefnd þemu, ein snerting til að skipta');
  add('Color treatment, previewed as swatches', 'Tratamento de cor, pré-visualizado como amostras', 'Tratamiento de color, previsualizado como muestras', 'Litameðferð, forskoðuð sem litasýni');
  add('A dozen+ seek & volume styles', 'Mais de 12 estilos de busca e volume', 'Más de 12 estilos de búsqueda y volumen', 'Yfir tugur stíla fyrir leit og hljóðstyrk');
  add('A dozen+ queue styles', 'Mais de 12 estilos de fila', 'Más de 12 estilos de cola', 'Yfir tugur biðraðarstíla');
  add('Pill, circle, squircle, leaf, drop…', 'Cápsula, círculo, squircle, folha, gota…', 'Píldora, círculo, squircle, hoja, gota…', 'Hylki, hringur, ferhringur, lauf, dropi…');
  add('Per-face editing', 'Edição por face', 'Edición por esfera', 'Breytingar fyrir hverja skífu');
  add('One watch, different moods.', 'Um relógio, estados diferentes.', 'Un reloj, distintos estados.', 'Eitt úr, ólíkar stemningar.');
  add('Store appearance independently for each face instead of forcing one global look. Change title, artist, clock, lyrics and track-time typography; choose album treatments, color harmony, progress style, panel layout and custom button shapes.', 'Armazene a aparência separadamente para cada face em vez de forçar um visual global. Altere a tipografia do título, artista, relógio, letras e tempo da faixa; escolha tratamentos da capa, harmonia de cores, estilo de progresso, layout dos painéis e formas personalizadas dos botões.', 'Guarda la apariencia de cada esfera por separado en lugar de imponer un aspecto global. Cambia la tipografía del título, artista, reloj, letras y tiempo; elige tratamientos del álbum, armonía de color, estilo de progreso, diseño del panel y formas personalizadas de botones.', 'Vistaðu útlit sjálfstætt fyrir hverja skífu í stað þess að þvinga fram eitt altækt útlit. Breyttu leturgerð titils, flytjanda, klukku, texta og spilunartíma; veldu meðferð plötuumslags, litasamræmi, framvindustíl, spjaldútlit og sérsniðin form hnappa.');
  add('Always-on display', 'Tela sempre ativa', 'Pantalla siempre activa', 'Sískjár');
  add('Designed for the low-power screen too.', 'Feito também para a tela de baixo consumo.', 'Diseñada también para la pantalla de bajo consumo.', 'Hannað líka fyrir orkusparandi skjáinn.');
  add('Choose an always-on presentation, artwork treatment, dim level, visibility and typography separately from the interactive player. The phone preview lets you check the ambient result before it reaches the watch.', 'Escolha uma apresentação sempre ativa, tratamento da arte, nível de brilho, visibilidade e tipografia separadamente do player interativo. A prévia no telefone permite conferir o resultado ambiente antes de enviá-lo ao relógio.', 'Elige una presentación siempre activa, tratamiento de la portada, nivel de atenuación, visibilidad y tipografía por separado del reproductor interactivo. La previsualización del teléfono permite comprobar el resultado antes de enviarlo al reloj.', 'Veldu framsetningu sískjásins, meðferð myndverks, dofnunarstig, sýnileika og leturgerð sjálfstætt frá gagnvirka spilaranum. Forskoðunin í símanum gerir þér kleift að athuga sískjáútkomuna áður en hún berst í úrið.');
  add('Readable by design', 'Legível por design', 'Legible por diseño', 'Læsilegt frá grunni');
  add('Make text hold its ground.', 'Faça o texto se destacar.', 'Haz que el texto destaque.', 'Láttu textann halda sínu.');
  add('Use album-aware or custom colors, adjustable opacity, shadow, outline and backdrop treatments for the title and artist. The same visual system also covers volume, seek, panels and queue rows.', 'Use cores baseadas na capa ou personalizadas, opacidade ajustável, sombra, contorno e fundos para o título e o artista. O mesmo sistema visual também cobre volume, busca, painéis e linhas da fila.', 'Usa colores basados en el álbum o personalizados, opacidad ajustable, sombra, contorno y fondos para el título y el artista. El mismo sistema visual también cubre volumen, búsqueda, paneles y filas de la cola.', 'Notaðu liti sem taka mið af plötuumslaginu eða sérsniðna liti, stillanlega ógegnsæi, skugga, útlínur og bakgrunnsmeðferð fyrir titil og flytjanda. Sama sjónræna kerfi nær líka yfir hljóðstyrk, leit, spjöld og raðir biðraðarinnar.');
  add('On the phone', 'No telefone', 'En el teléfono', 'Í símanum');
  add('Preview before you send.', 'Veja antes de enviar.', 'Previsualiza antes de enviar.', 'Forskoðaðu áður en þú sendir.');
  add('The Watch tab mirrors the current track or a built-in sample and updates as you edit. Apply, duplicate, rename or delete profiles without changing the others.', 'A aba Relógio reproduz a faixa atual ou uma amostra integrada e atualiza enquanto você edita. Aplique, duplique, renomeie ou exclua perfis sem alterar os outros.', 'La pestaña Reloj refleja la canción actual o una muestra integrada y se actualiza mientras editas. Aplica, duplica, renombra o elimina perfiles sin cambiar los demás.', 'Úr-flipinn endurspeglar núverandi lag eða innbyggt sýnishorn og uppfærist jafnóðum og þú breytir. Notaðu, afritaðu, endurnefndu eða eyddu stillingum án þess að hafa áhrif á hinar.');

  add('Community themes', 'Temas da comunidade', 'Temas de la comunidad', 'Samfélagsþemu');
  add('Borrow a look. Make it yours.', 'Pegue uma aparência. Faça dela sua.', 'Toma un estilo. Hazlo tuyo.', 'Fáðu lánað útlit. Gerðu það að þínu.');
  add('Browse a public catalogue of appearance profiles from the phone, inspect the full set of local previews and install only what you choose. A community theme is a starting point, not a locked preset.', 'Navegue por um catálogo público de perfis visuais no telefone, confira todas as prévias locais e instale apenas o que escolher. Um tema da comunidade é um ponto de partida, não um preset bloqueado.', 'Explora un catálogo público de perfiles visuales desde el teléfono, revisa todas las previsualizaciones locales e instala solo lo que elijas. Un tema de la comunidad es un punto de partida, no un ajuste bloqueado.', 'Skoðaðu opinbert safn útlitsstillinga úr símanum, skoðaðu allar staðbundnar forskoðanir og settu aðeins upp það sem þú velur. Samfélagsþema er upphafspunktur, ekki læst forstillt val.');
  add('Community catalogue', 'Catálogo da comunidade', 'Catálogo de la comunidad', 'Safn samfélagsins');
  add('Made on a phone', 'Criado no telefone', 'Hecho en un teléfono', 'Búið til í síma');
  add('Discover a starting point.', 'Descubra um ponto de partida.', 'Descubre un punto de partida.', 'Uppgötvaðu upphafspunkt.');
  add('Search by theme or author, filter by base face, sort by newest, likes or installs, and inspect the full local previews before adding anything to your library.', 'Busque por tema ou autor, filtre pela face base, ordene pelos mais novos, curtidos ou instalados e confira todas as prévias locais antes de adicionar algo à sua biblioteca.', 'Busca por tema o autor, filtra por esfera base, ordena por novedades, favoritos o instalaciones y revisa las previsualizaciones locales antes de añadir algo a tu biblioteca.', 'Leitaðu eftir þema eða höfundi, síaðu eftir grunnskífu, raðaðu eftir nýjustu, „líkar við" eða uppsetningum, og skoðaðu allar staðbundnar forskoðanir áður en þú bætir einhverju við safnið þitt.');
  add('Install, then make it yours.', 'Instale e faça do seu jeito.', 'Instala y hazlo tuyo.', 'Settu upp og gerðu það svo að þínu.');
  add('Preview Player, always-on, Volume, Progress, Quick panel and Queue. Installing creates a local copy you can apply, edit, duplicate or remove independently.', 'Visualize Player, tela sempre ativa, Volume, Progresso, Painel rápido e Fila. A instalação cria uma cópia local que você pode aplicar, editar, duplicar ou remover de forma independente.', 'Previsualiza Player, pantalla siempre activa, Volumen, Progreso, Panel rápido y Cola. La instalación crea una copia local que puedes aplicar, editar, duplicar o eliminar de forma independiente.', 'Forskoðaðu Player, sískjá, hljóðstyrk, framvindu, flýtispjald og biðröð. Uppsetning býr til staðbundið afrit sem þú getur notað, breytt, afritað eða fjarlægt sjálfstætt.');
  add('Share your own work.', 'Compartilhe seu próprio trabalho.', 'Comparte tu trabajo.', 'Deildu þínu eigin verki.');
  add('Submit a user-owned theme for review with a public name and pseudonym or Anonymous label. Authors can optionally attach one real watch photo.', 'Envie um tema de sua autoria para análise com um nome público e pseudônimo ou o rótulo Anônimo. Os autores podem anexar opcionalmente uma foto real do relógio.', 'Envía un tema propio para revisión con un nombre público y un seudónimo o la etiqueta Anónimo. Los autores pueden adjuntar opcionalmente una foto real del reloj.', 'Sendu inn þitt eigið þema til yfirferðar með opinberu nafni og dulnefni eða merkinu „Nafnlaust". Höfundar geta að vild bætt við einni raunverulegri ljósmynd af úrinu.');
  add('Keep public publishing accountable.', 'Mantenha a publicação pública responsável.', 'Mantén la publicación pública bajo control.', 'Höldum opinberri birtingu ábyrgri.');
  add('Browsing and installing need no account. Likes, installs and reports are private, while approved profiles reach the static catalogue through moderation.', 'Navegar e instalar não exige conta. Curtidas, instalações e denúncias são privadas, enquanto perfis aprovados chegam ao catálogo estático por meio de moderação.', 'Explorar e instalar no requiere cuenta. Los favoritos, instalaciones e informes son privados y los perfiles aprobados llegan al catálogo estático mediante moderación.', 'Skoðun og uppsetning krefjast engrar notandaaðgangs. „Líkar við", uppsetningar og tilkynningar eru einkamál, en samþykktar stillingar rata í fasta safnið gegnum yfirferð stjórnenda.');

  add('Compatibility & privacy', 'Compatibilidade e privacidade', 'Compatibilidad y privacidad', 'Samhæfni og persónuvernd');
  add('Built around Android\'s media standards.', 'Baseado nos padrões de mídia do Android.', 'Basado en los estándares multimedia de Android.', 'Byggt í kringum miðlunarstaðla Android.');
  add('Svartifoss does not replace your music app, stream audio or ask for a Svartifoss cloud account. It connects the phone and watch locally, then uses the media contracts each installed player makes available.', 'O Svartifoss não substitui seu app de música, transmite áudio nem pede uma conta na nuvem do Svartifoss. Ele conecta telefone e relógio localmente e usa os contratos de mídia que cada player instalado disponibiliza.', 'Svartifoss no reemplaza tu app de música, no transmite audio ni pide una cuenta en la nube de Svartifoss. Conecta el teléfono y el reloj localmente y usa los contratos multimedia que ofrece cada reproductor instalado.', 'Svartifoss kemur ekki í stað tónlistarforritsins þíns, streymir engu hljóði og biður ekki um Svartifoss-skýjareikning. Það tengir símann og úrið staðbundið og notar síðan þau miðlunarviðmót sem hvert uppsett forrit býður upp á.');
  add('Local first', 'Local em primeiro lugar', 'Local primero', 'Staðbundið fyrst');
  add('Your controls do not pass through a Svartifoss server.', 'Seus controles não passam por um servidor Svartifoss.', 'Tus controles no pasan por un servidor de Svartifoss.', 'Stjórnun þín fer aldrei um Svartifoss-netþjón.');
  add('The phone and watch talk over the local Wearable Data Layer. Ordinary control works without creating an account or giving a third party your listening activity.', 'O telefone e o relógio se comunicam pela Wearable Data Layer local. O controle comum funciona sem criar uma conta ou entregar sua atividade de reprodução a terceiros.', 'El teléfono y el reloj se comunican mediante la Wearable Data Layer local. El control normal funciona sin crear una cuenta ni entregar tu actividad de escucha a terceros.', 'Síminn og úrið tala saman um staðbundið Wearable Data Layer. Venjuleg stjórnun virkar án þess að stofna aðgang eða gefa þriðja aðila upplýsingar um hlustun þína.');
  add('Phone', 'Telefone', 'Teléfono', 'Sími');
  add('Media session', 'Sessão de mídia', 'Sesión multimedia', 'Miðlunarlota');
  add('Wear OS watch', 'Relógio Wear OS', 'Reloj Wear OS', 'Wear OS úr');
  add('Your inputs', 'Seus comandos', 'Tus entradas', 'Inntökin þín');
  add('Basic playback', 'Reprodução básica', 'Reproducción básica', 'Grunnafspilun');
  add('A usable Android MediaSession provides play, pause, skip, position and other transport commands.', 'Uma MediaSession do Android utilizável oferece reproduzir, pausar, avançar, posição e outros comandos de transporte.', 'Una MediaSession de Android funcional ofrece reproducir, pausar, saltar, posición y otros comandos de transporte.', 'Nothæf Android MediaSession veitir spilun, hlé, að sleppa lagi, staðsetningu og aðrar spilunarskipanir.');
  add('Queue', 'Fila', 'Cola', 'Biðröð');
  add('The player must publish a queue. Otherwise Svartifoss falls back to recent track history.', 'O player precisa publicar uma fila. Caso contrário, o Svartifoss usa o histórico recente de faixas.', 'El reproductor debe publicar una cola. De lo contrario, Svartifoss usa el historial reciente de canciones.', 'Spilarinn verður að birta biðröð. Annars reiðir Svartifoss sig á nýlega lagasögu.');
  add('Library & search', 'Biblioteca e busca', 'Biblioteca y búsqueda', 'Safn og leit');
  add('Browsing and in-app search depend on a discoverable MediaBrowserService or compatible search command.', 'A navegação e a busca no app dependem de um MediaBrowserService detectável ou de um comando de busca compatível.', 'La navegación y la búsqueda dentro de la app dependen de un MediaBrowserService detectable o de un comando de búsqueda compatible.', 'Flettingar og leit í appinu byggja á finnanlegri MediaBrowserService eða samhæfðri leitarskipun.');
  add('Like, shuffle & repeat', 'Curtir, aleatório e repetição', 'Favorito, aleatorio y repetición', '„Líkar við", slembiröðun og endurtekning');
  add('Extra actions appear when the notification or media session publishes them.', 'Ações extras aparecem quando a notificação ou a sessão de mídia as publica.', 'Las acciones adicionales aparecen cuando la notificación o la sesión multimedia las publica.', 'Aukaaðgerðir birtast þegar tilkynningin eða miðlunarlotan birtir þær.');
  add('Rich metadata', 'Metadados completos', 'Metadatos enriquecidos', 'Ítarleg lýsigögn');
  add('Tags come from the player; local file details need media access; optional MusicBrainz lookup is off by default.', 'As tags vêm do player; detalhes de arquivos locais exigem acesso à mídia; a consulta opcional ao MusicBrainz vem desativada por padrão.', 'Las etiquetas proceden del reproductor; los detalles de archivos locales necesitan acceso multimedia; la consulta opcional a MusicBrainz está desactivada por defecto.', 'Merki koma frá spilaranum; upplýsingar um staðbundnar skrár þurfa aðgang að miðlum; valfrjáls uppfletting hjá MusicBrainz er sjálfgefið óvirk.');
  add('Streaming shortcuts', 'Atalhos de streaming', 'Accesos directos de streaming', 'Streymisflýtileiðir');
  add('Direct playback depends on the target service. If it declines, Svartifoss opens the link in that service or a browser.', 'A reprodução direta depende do serviço de destino. Se ele recusar, o Svartifoss abre o link no serviço ou em um navegador.', 'La reproducción directa depende del servicio de destino. Si la rechaza, Svartifoss abre el enlace en ese servicio o en un navegador.', 'Bein afspilun fer eftir markþjónustunni. Ef hún hafnar því opnar Svartifoss tengilinn í þeirri þjónustu eða vafra.');
  add('What stays local:', 'O que permanece local:', 'Lo que permanece local:', 'Það sem er alltaf staðbundið:');

  add('The name', 'O nome', 'El nombre', 'Nafnið');
  add('Black Falls', 'Cachoeira Negra', 'Cascada Negra', 'Svartur foss');
  add('Install', 'Instalação', 'Instalación', 'Uppsetning');
  add('Sideload it. Takes a minute.', 'Instale por sideload. Leva um minuto.', 'Instálala por sideload. Tarda un minuto.', 'Settu það upp handvirkt. Tekur eina mínútu.');
  add('Install the two APKs directly from GitHub Releases onto your phone and watch. The phone needs Android notification access to discover the active media session; the watch app is installed separately. After the first setup, the phone can notify you about releases and send watch updates over Bluetooth.', 'Instale os dois APKs diretamente pelos GitHub Releases no telefone e no relógio. O telefone precisa de acesso às notificações do Android para descobrir a sessão de mídia ativa; o app do relógio é instalado separadamente. Depois da configuração inicial, o telefone pode avisar sobre lançamentos e enviar atualizações ao relógio por Bluetooth.', 'Instala los dos APK directamente desde GitHub Releases en tu teléfono y reloj. El teléfono necesita acceso a las notificaciones de Android para descubrir la sesión multimedia activa; la app del reloj se instala por separado. Después de la configuración inicial, el teléfono puede avisarte de nuevos lanzamientos y enviar actualizaciones al reloj por Bluetooth.', 'Settu upp bæði APK-skrárnar beint úr GitHub Releases, í símann og úrið. Síminn þarf aðgang að Android-tilkynningum til að finna virku miðlunarlotuna; úraforritið er sett upp sérstaklega. Eftir fyrstu uppsetningu getur síminn látið þig vita af nýjum útgáfum og sent uppfærslur til úrsins um Bluetooth.');
  add('Install on your phone', 'Instale no telefone', 'Instala en tu teléfono', 'Settu upp í símanum');
  add('Download the phone APK below and open it. Android may ask you to allow installs from your browser first.', 'Baixe o APK do telefone abaixo e abra-o. O Android pode pedir primeiro autorização para instalar apps pelo navegador.', 'Descarga el APK del teléfono y ábrelo. Android puede pedirte primero que permitas instalaciones desde el navegador.', 'Sæktu APK-skrána fyrir símann hér fyrir neðan og opnaðu hana. Android gæti fyrst beðið þig um að leyfa uppsetningar úr vafranum.');
  add('Install on your watch', 'Instale no relógio', 'Instala en tu reloj', 'Settu upp í úrinu');
  add('Open both apps', 'Abra os dois apps', 'Abre las dos apps', 'Opnaðu bæði forritin');
  add('They find each other automatically over the Wearable Data Layer — no account, no pairing code, nothing else to configure.', 'Eles se encontram automaticamente pela Wearable Data Layer — sem conta, código de pareamento ou qualquer outra configuração.', 'Se encuentran automáticamente mediante la Wearable Data Layer — sin cuenta, código de emparejamiento ni nada más que configurar.', 'Þau finna hvort annað sjálfkrafa um Wearable Data Layer — enginn notandaaðgangur, enginn pörunarkóði, ekkert annað að stilla.');
  add('Recommended · phone & watch', 'Recomendado · telefone e relógio', 'Recomendado · teléfono y reloj', 'Mælt með · sími og úr');
  add('Phone · Android 6.0+ (sideload)', 'Telefone · Android 6.0+ (sideload)', 'Teléfono · Android 6.0+ (sideload)', 'Sími · Android 6.0+ (handvirk uppsetning)');
  add('Watch · Wear OS 2 or newer (sideload)', 'Relógio · Wear OS 2 ou mais recente (sideload)', 'Reloj · Wear OS 2 o posterior (sideload)', 'Úr · Wear OS 2 eða nýrra (handvirk uppsetning)');
  add('License', 'Licença', 'Licencia', 'Leyfi');

  // 4.0 additions: nav, the Compare tool, the possibilities/lyrics mural, the real-screenshot
  // gallery and the updated Content/Install copy.
  add('Compare', 'Comparar', 'Comparar', 'Bera saman');
  add('Gallery', 'Galeria', 'Galería', 'Myndasafn');
  add('Svartifoss is not a fixed three-button remote. It gives the watch a complete action catalogue, then lets you decide which input should run which command.', 'O Svartifoss não é um controle remoto fixo de três botões. Ele dá ao relógio um catálogo completo de ações e deixa você decidir qual entrada executa qual comando.', 'Svartifoss no es un control remoto fijo de tres botones. Le da al reloj un catálogo completo de acciones y te deja decidir qué entrada ejecuta qué comando.', 'Svartifoss er engin föst fjarstýring með þremur hnöppum. Það gefur úrinu heilt safn aðgerða og lætur þig svo ákveða hvaða inntak keyrir hvaða skipun.');
  add('Real search results', 'Resultados de busca reais', 'Resultados de búsqueda reales', 'Raunverulegar leitarniðurstöður');
  add('Verse, synced live', 'Verse, sincronizado ao vivo', 'Verse, sincronizado en vivo', 'Verse, samstillt í beinni');
  add('Saved on the phone', 'Salvo no telefone', 'Guardado en el teléfono', 'Vistað í símanum');
  add('Try it yourself', 'Experimente você mesmo', 'Pruébalo tú mismo', 'Prófaðu sjálfur');
  add('Pick a face. See it on four real tracks.', 'Escolha uma face. Veja em quatro faixas reais.', 'Elige una esfera. Velo en cuatro canciones reales.', 'Veldu skífu. Sjáðu hana á fjórum raunverulegum lögum.');
  add('Every face rebuilds its palette, gradients and progress from the cover that\'s actually playing — so the same layout looks different from one album to the next. Pick any of the 15 default faces below and compare it live across four real tracks, captured back to back on an actual watch.', 'Cada face reconstrói sua paleta, gradientes e progresso a partir da capa que está tocando de verdade — então o mesmo layout muda de um álbum para outro. Escolha qualquer uma das 15 faces padrão abaixo e compare ao vivo em quatro faixas reais, capturadas uma após a outra em um relógio de verdade.', 'Cada esfera reconstruye su paleta, degradados y progreso a partir de la portada que realmente está sonando — así que el mismo diseño cambia de un álbum a otro. Elige cualquiera de las 15 esferas predeterminadas de abajo y compárala en vivo en cuatro canciones reales, capturadas una tras otra en un reloj real.', 'Sérhver skífa endurbyggir litatóna sína, litstigla og framvindu út frá umslaginu sem er raunverulega í spilun — þannig að sama útlitið lítur öðruvísi út frá einni plötu til annarrar. Veldu eina af 15 sjálfgefnu skífunum hér fyrir neðan og berðu hana saman í beinni á fjórum raunverulegum lögum, teknum hverju á eftir öðru á alvöru úri.');
  add('Volume, seek, the quick-actions panel, the queue and the lyrics screen each carry a full catalog of styles — a sample of each is below. Mix and match them freely; none of it is tied to whichever face is running underneath.', 'Volume, busca, painel de ações rápidas, fila e tela de letras têm cada um um catálogo completo de estilos — abaixo está uma amostra de cada um. Combine livremente; nada disso fica preso à face que está sendo usada.', 'El volumen, la búsqueda, el panel de acciones rápidas, la cola y la pantalla de letras tienen cada uno un catálogo completo de estilos — abajo hay una muestra de cada uno. Combínalos libremente; nada está ligado a la esfera que se esté usando.', 'Hljóðstyrkur, leit, flýtiaðgerðaspjaldið, biðröðin og textaskjárinn bera hvert um sig fullt safn af stílum — sýnishorn af hverjum er hér fyrir neðan. Blandaðu þeim saman að vild; ekkert af þessu er bundið því hvaða skífa er í gangi undir niðri.');
  add('Volume', 'Volume', 'Volumen', 'Hljóðstyrkur');
  add('Seek', 'Busca', 'Búsqueda', 'Leit');
  add('Quick panel', 'Painel rápido', 'Panel rápido', 'Flýtispjald');
  add('Arc ring', 'Anel em arco', 'Anillo en arco', 'Bogahringur');
  add('Gradient ring', 'Anel gradiente', 'Anillo degradado', 'Litstiglahringur');
  add('High contrast', 'Alto contraste', 'Alto contraste', 'Mikil birtuskil');
  add('Tick bezel', 'Borda com marcas', 'Bisel con marcas', 'Jaðarhringur með strikum');
  add('Glow arc', 'Arco brilhante', 'Arco brillante', 'Ljómandi bogi');
  add('Indigo blur', 'Desfoque índigo', 'Desenfoque índigo', 'Indígóblá móða');
  add('Teal gradient', 'Gradiente petróleo', 'Degradado turquesa', 'Blágrænn litstigull');
  add('Up Next preview', 'Prévia da próxima faixa', 'Vista previa de siguiente', 'Forskoðun á næsta lagi');
  add('Warm blur', 'Desfoque quente', 'Desenfoque cálido', 'Hlý móða');
  add('Glass pills', 'Cápsulas de vidro', 'Píldoras de cristal', 'Glerhylki');
  add('Pink highlight', 'Destaque rosa', 'Resaltado rosa', 'Bleikur áherslulitur');
  add('Coral rows', 'Linhas coral', 'Filas coral', 'Kóralrauðar raðir');
  add('Sepia rows', 'Linhas sépia', 'Filas sepia', 'Sepíuraðir');
  add('Lyrics', 'Letras', 'Letras', 'Textar');
  add('the Verse face, synced live', 'a face Verse, sincronizada ao vivo', 'la esfera Verse, sincronizada en vivo', 'Verse-skífan, samstillt í beinni');
  add('Glow', 'Brilho', 'Brillo', 'Ljómi');
  add('Red highlight', 'Destaque vermelho', 'Resaltado rojo', 'Rauður áherslulitur');
  add('Gold highlight', 'Destaque dourado', 'Resaltado dorado', 'Gylltur áherslulitur');
  add('Gold, short line', 'Dourado, linha curta', 'Dorado, línea corta', 'Gull, stutt lína');
  add('Underline', 'Sublinhado', 'Subrayado', 'Undirstrikun');
  add('Blue highlight', 'Destaque azul', 'Resaltado azul', 'Blár áherslulitur');
  add('A dozen+ album art treatments', 'Mais de 12 tratamentos de capa', 'Más de 12 tratamientos de portada', 'Yfir tugur meðferða fyrir plötuumslag');
  add('Every font, set in itself', 'Cada fonte, escrita nela mesma', 'Cada tipografía, escrita en sí misma', 'Sérhvert letur, sett fram í sjálfu sér');
  add('A dozen+ panel backgrounds', 'Mais de 12 fundos de painel', 'Más de 12 fondos de panel', 'Yfir tugur bakgrunna fyrir spjöld');
  add('A dozen+ volume styles', 'Mais de 12 estilos de volume', 'Más de 12 estilos de volumen', 'Yfir tugur stíla fyrir hljóðstyrk');
  add('Always-on, styled per face', 'Tela sempre ativa, com estilo por face', 'Pantalla siempre activa, con estilo por esfera', 'Sískjár, stílaður fyrir hverja skífu');
  add('Real screenshots', 'Capturas reais', 'Capturas reales', 'Raunverulegar skjámyndir');
  add('Then make it yours', 'Depois, faça do seu jeito', 'Luego, hazlo tuyo', 'Gerðu það svo að þínu');
  add('Browse', 'Navegar', 'Explorar', 'Skoða');
  add('Search, filter, sort', 'Buscar, filtrar, ordenar', 'Buscar, filtrar, ordenar', 'Leita, sía, raða');
  add('Every screen, for real', 'Cada tela, de verdade', 'Cada pantalla, de verdad', 'Hver skjár, í alvöru');
  add('Not mockups. Screenshots.', 'Nada de simulações. Capturas reais.', 'Nada de simulaciones. Capturas reales.', 'Engar eftirlíkingar. Skjámyndir.');
  add('A wider look at 4.0, straight off a real watch: more faces, more panels, more overlays — scrolling by on its own, nothing here is staged. Hover to pause on any one of them.', 'Um panorama mais amplo da 4.0, direto de um relógio de verdade: mais faces, mais painéis, mais sobreposições — passando sozinho, nada aqui é encenado. Passe o mouse para pausar em qualquer uma delas.', 'Un vistazo más amplio a la 4.0, directo de un reloj real: más esferas, más paneles, más superposiciones — desplazándose solas, nada aquí está preparado. Pasa el cursor para pausar en cualquiera de ellas.', 'Víðara yfirlit yfir 4.0, beint af alvöru úri: fleiri skífur, fleiri spjöld, fleiri yfirlög — skrunar áfram af sjálfu sér, ekkert hér er sviðsett. Settu bendilinn yfir til að gera hlé á hverri sem er.');
  add('Face picker', 'Seletor de faces', 'Selector de esferas', 'Skífuval');
  add('Quick actions panel', 'Painel de ações rápidas', 'Panel de acciones rápidas', 'Flýtiaðgerðaspjald');
  add('Menu', 'Menu', 'Menú', 'Valmynd');
  add('Privacy Policy', 'Política de privacidade', 'Política de privacidad', 'Persónuverndarstefna');

  // Landing-page navigation, setup and interaction feedback.
  add("Skip to content", "Pular para o conteúdo", "Saltar al contenido", "Fara beint í efni");
  add("Main navigation", "Navegação principal", "Navegación principal", "Aðalvalmynd");
  add("Toggle navigation", "Abrir ou fechar menu", "Abrir o cerrar menú", "Sýna/fela valmynd");
  add("Media controls for Wear OS, made yours.", "Controles de mídia no Wear OS, do seu jeito.", "Controles multimedia en Wear OS, a tu manera.", "Miðlunarstýring fyrir Wear OS, að þínum hætti.");
  add("Control music, follow lyrics and make every gesture your own. Svartifoss connects the player on your phone to a watch that feels like you.", "Controle sua música, acompanhe as letras e personalize cada gesto. O Svartifoss conecta o player do seu telefone a um relógio com a sua cara.", "Controla tu música, sigue las letras y personaliza cada gesto. Svartifoss conecta el reproductor de tu teléfono a un reloj que refleja tu estilo.", "Stjórnaðu tónlist, fylgstu með textum og gerðu sérhverja bendingu að þinni eigin. Svartifoss tengir spilarann í símanum þínum við úr sem endurspeglar þig.");
  add("Explore the layouts", "Explore os layouts", "Explora los diseños", "Skoðaðu útlitin");
  add("Android phone + Wear OS watch", "Telefone Android + relógio Wear OS", "Teléfono Android + reloj Wear OS", "Android sími + Wear OS úr");
  add("Installation options", "Opções de instalação", "Opciones de instalación", "Uppsetningarmöguleikar");
  add("Player layouts", "Layouts do player", "Diseños del reproductor", "Útlit spilarans");
  add("No account needed", "Sem precisar de conta", "Sin necesidad de cuenta", "Enginn aðgangur nauðsynlegur");
  add("Local", "Local", "Local", "Staðbundið");
  add("See releases", "Ver versões", "Ver versiones", "Sjá útgáfur");
  add("Choose your controls and style on the phone. Your paired watch updates automatically, with no Svartifoss account needed.", "Escolha os controles e o visual pelo telefone. Seu relógio pareado recebe as mudanças automaticamente, sem precisar de uma conta Svartifoss.", "Elige tus controles y tu estilo desde el teléfono. Tu reloj vinculado recibe los cambios automáticamente, sin necesitar una cuenta de Svartifoss.", "Veldu stýringar þínar og útlit í símanum. Parað úrið þitt uppfærist sjálfkrafa, án þess að Svartifoss-aðgangs sé þörf.");
  add("Connection & privacy details", "Detalhes de conexão e privacidade", "Detalles de conexión y privacidad", "Nánar um tengingu og persónuvernd");
  add("Choose one of 15 sample layouts to see how it adapts to four album covers. These are real captures from a watch.", "Escolha um dos 15 layouts de exemplo e veja como ele se adapta a quatro capas de álbum. São capturas reais de um relógio.", "Elige uno de los 15 diseños de muestra para ver cómo se adapta a cuatro portadas de álbum. Son capturas reales de un reloj.", "Veldu eitt af 15 sýnishornaútlitum til að sjá hvernig það aðlagast fjórum plötuumslögum. Þetta eru raunverulegar upptökur af úri.");
  add("Swipe or use the arrows to browse. Select an image to enlarge.", "Deslize ou use as setas para navegar. Selecione uma imagem para ampliar.", "Desliza o usa las flechas para explorar. Selecciona una imagen para ampliarla.", "Strjúktu eða notaðu örvarnar til að skoða. Veldu mynd til að stækka hana.");
  add("Explore real watch captures, from album-inspired layouts to lyrics and quick controls. Browse at your own pace and open any image for a closer look.", "Explore capturas reais do relógio, de layouts inspirados nas capas de álbum a letras e controles rápidos. Navegue no seu ritmo e abra qualquer imagem para ver os detalhes.", "Explora capturas reales del reloj, desde diseños inspirados en portadas hasta letras y controles rápidos. Navega a tu ritmo y abre cualquier imagen para ver los detalles.", "Skoðaðu raunverulegar upptökur af úrinu, allt frá útlitum innblásnum af plötuumslögum til texta og flýtistýringa. Flettu á þínum hraða og opnaðu hvaða mynd sem er til að skoða nánar.");
  add("Get started", "Comece aqui", "Empieza aquí", "Byrjaðu núna");
  add("Your next track is a tap away.", "Sua próxima faixa está a um toque.", "Tu próxima canción está a un toque.", "Næsta lag er eina snertingu í burtu.");
  add("Install Svartifoss on both your Android phone and Wear OS watch, then open both apps to get connected.", "Instale o Svartifoss no telefone Android e no relógio Wear OS. Depois, abra os dois apps para conectar.", "Instala Svartifoss en tu teléfono Android y tu reloj Wear OS. Después, abre ambas apps para conectarlos.", "Settu upp Svartifoss bæði í Android símanum og Wear OS úrinu, opnaðu síðan bæði forritin til að tengjast.");
  add("Recommended", "Recomendado", "Recomendado", "Mælt með");
  add("One listing. Both devices.", "Uma página. Dois dispositivos.", "Una ficha. Dos dispositivos.", "Ein færsla. Bæði tækin.");
  add("Get the phone and watch apps from Google Play. On your phone, allow notification access so Svartifoss can find your music player.", "Baixe os apps para telefone e relógio na Google Play. No telefone, permita o acesso às notificações para que o Svartifoss encontre seu player de música.", "Descarga las apps para teléfono y reloj desde Google Play. En el teléfono, permite el acceso a las notificaciones para que Svartifoss encuentre tu reproductor de música.", "Sæktu forritin fyrir síma og úr á Google Play. Leyfðu aðgang að tilkynningum í símanum svo Svartifoss finni tónlistarspilarann þinn.");
  add("Android 6.0+ · Wear OS 2 or newer", "Android 6.0+ · Wear OS 2 ou mais recente", "Android 6.0+ · Wear OS 2 o posterior", "Android 6.0+ · Wear OS 2 eða nýrra");
  add("Prefer APKs? Install from GitHub", "Prefere APKs? Instale pelo GitHub", "¿Prefieres APKs? Instala desde GitHub", "Viltu frekar APK-skrár? Settu upp af GitHub");
  add("Download both APKs for a manual installation. Open the phone app and allow notification access, then open the watch app.", "Baixe os dois APKs para instalar manualmente. Abra o app do telefone e permita o acesso às notificações. Depois, abra o app do relógio.", "Descarga ambos APKs para una instalación manual. Abre la app del teléfono y permite el acceso a las notificaciones. Después, abre la app del reloj.", "Sæktu báðar APK-skrárnar fyrir handvirka uppsetningu. Opnaðu símaforritið og leyfðu aðgang að tilkynningum, opnaðu síðan úraforritið.");
  add("Check your setup", "Prepare seus dispositivos", "Prepara tus dispositivos", "Athugaðu uppsetninguna þína");
  add("Previous images", "Imagens anteriores", "Imágenes anteriores", "Fyrri myndir");
  add("Next images", "Próximas imagens", "Siguientes imágenes", "Næstu myndir");
  add("Scroll left", "Rolar para a esquerda", "Desplazar a la izquierda", "Skruna til vinstri");
  add("Scroll right", "Rolar para a direita", "Desplazar a la derecha", "Skruna til hægri");
  add("Choose a now-playing face", "Escolher um layout do player", "Elegir un diseño del reproductor", "Veldu núspilunarskífu");
  add("Open enlarged image", "Abrir imagem ampliada", "Abrir imagen ampliada", "Opna stækkaða mynd");
  add("Svartifoss preview", "Prévia do Svartifoss", "Vista previa de Svartifoss", "Forskoðun Svartifoss");
  add("Expand", "Ampliar", "Ampliar", "Stækka");
  add("Loading preview", "Carregando prévia", "Cargando vista previa", "Hleð forskoðun");
  add("Preview updated", "Prévia atualizada", "Vista previa actualizada", "Forskoðun uppfærð");
  add("Preview unavailable", "Não foi possível carregar a prévia. Tente novamente.", "No se pudo cargar la vista previa. Inténtalo de nuevo.", "Ekki tókst að hlaða forskoðun. Reyndu aftur.");
  add("Pause slideshow", "Pausar apresentação", "Pausar presentación", "Gera hlé á skyggnusýningu");
  add("Play slideshow", "Reproduzir apresentação", "Reproducir presentación", "Spila skyggnusýningu");
  add("Configuring Svartifoss on the phone app", "Configuração do Svartifoss no app do telefone", "Configuración de Svartifoss en la app del teléfono", "Stillingar Svartifoss í símaforritinu");
  add("The configured now-playing screen live on the watch, cycling through faces", "Layouts do player configurados no relógio", "Diseños del reproductor configurados en el reloj", "Stillt útlit spilarans, í beinni í úrinu, skiptir á milli skífa");

  var htmlCopy = {
    'hero.badge': {
      'pt-BR': '<span class="dot" aria-hidden="true">●</span> Código aberto · Feito para Wear OS',
      es: '<span class="dot" aria-hidden="true">●</span> Código abierto · Hecho para Wear OS',
      is: '<span class="dot" aria-hidden="true">●</span> Opinn hugbúnaður · Gert fyrir Wear OS'
    },
    'sync.title': {
      'pt-BR': 'Configure no telefone.<br>Ele aparece no relógio na hora.',
      es: 'Configúralo en el teléfono.<br>Aparece en tu reloj al instante.',
      is: 'Stilltu það í símanum.<br>Það birtist samstundis í úrinu.'
    },
    'story.description': {
      'pt-BR': '<strong>Svartifoss</strong> significa "Cachoeira Negra" em islandês — nome inspirado na cachoeira do Parque Nacional Vatnajökull, onde colunas escuras de basalto emolduram uma cascata estreita. Assim como a água sobre a pedra, a música flui pelo app.',
      es: '<strong>Svartifoss</strong> significa "Cascada Negra" en islandés — toma su nombre de la cascada del Parque Nacional Vatnajökull, donde columnas oscuras de basalto enmarcan un estrecho salto de agua. Como el agua sobre la piedra, la música fluye por la app.',
      is: '<strong>Svartifoss</strong> ber nafn samnefnds foss í Vatnajökulsþjóðgarði, þar sem dökkar stuðlabergssúlur ramma inn mjótt en tignarlegt vatnsfall. Líkt og vatnið sem streymir niður bergið, flæðir tónlistin um appið.'
    },
    'install.watch.step': {
      'pt-BR': 'Baixe o APK do relógio e faça o sideload no seu Wear OS — o jeito mais fácil é usar <a href="https://www.xda-developers.com/wear-installer-sideload-wear-os-apps/">Wear Installer <svg class="icon"><use href="#i-open-new"/></svg></a>.',
      es: 'Descarga el APK del reloj e instálalo por sideload en tu Wear OS — la forma más fácil es usar <a href="https://www.xda-developers.com/wear-installer-sideload-wear-os-apps/">Wear Installer <svg class="icon"><use href="#i-open-new"/></svg></a>.',
      is: 'Sæktu APK-skrána fyrir úrið og settu hana upp handvirkt (sideload) í Wear OS úrið þitt — einfaldast er að nota <a href="https://www.xda-developers.com/wear-installer-sideload-wear-os-apps/">Wear Installer <svg class="icon"><use href="#i-open-new"/></svg></a>.'
    },
    'download.play': {
      'pt-BR': '<svg class="icon"><use href="#i-google-play"/></svg> Baixar na Google Play',
      es: '<svg class="icon"><use href="#i-google-play"/></svg> Consíguelo en Google Play',
      is: '<svg class="icon"><use href="#i-google-play"/></svg> Sæktu á Google Play'
    },
    'download.phone': {
      'pt-BR': '<svg class="icon"><use href="#i-send-mobile"/></svg> Baixar APK do telefone',
      es: '<svg class="icon"><use href="#i-send-mobile"/></svg> Descargar APK del teléfono',
      is: '<svg class="icon"><use href="#i-send-mobile"/></svg> Sækja APK fyrir síma'
    },
    'download.watch': {
      'pt-BR': '<svg class="icon"><use href="#i-watch-down"/></svg> Baixar APK do relógio',
      es: '<svg class="icon"><use href="#i-watch-down"/></svg> Descargar APK del reloj',
      is: '<svg class="icon"><use href="#i-watch-down"/></svg> Sækja APK fyrir úr'
    },
    'compat.note': {
      'pt-BR': '<strong>O que permanece local:</strong> o controle entre telefone e relógio usa a Wearable Data Layer e não precisa de uma conta Svartifoss. Recursos de rede são opcionais ou explícitos: atualizações, catálogo de temas da comunidade, diagnósticos opcionais, avisos, letras, enriquecimento de metadados e capas de atalhos podem ser controlados nas configurações do app.',
      es: '<strong>Lo que permanece local:</strong> el control entre teléfono y reloj usa la Wearable Data Layer y no necesita una cuenta de Svartifoss. Las funciones de red son opcionales o explícitas: actualizaciones, catálogo de temas de la comunidad, diagnósticos opcionales, avisos, letras, enriquecimiento de metadatos y portadas de accesos directos se pueden controlar en los ajustes de la app.',
      is: '<strong>Það sem er alltaf staðbundið:</strong> stjórn milli síma og úrs fer fram um Wearable Data Layer og krefst ekki Svartifoss-aðgangs. Netaðgerðir eru valfrjálsar eða skýrt merktar: uppfærslur, safn samfélagsþema, valfrjáls villugreining, tilkynningar, textar, auðgun lýsigagna og myndir fyrir flýtileiðir má stýra í stillingum appsins.'
    },
    'install.playStore': {
      'pt-BR': '<strong>O Svartifoss também está na Google Play</strong> — uma única ficha cobre telefone e relógio, sem sideload. Os lançamentos no GitHub continuam úteis para pré-lançamentos e para atualizar uma instalação por sideload já existente.',
      es: '<strong>Svartifoss también está en Google Play</strong> — una sola ficha cubre el teléfono y el reloj, sin sideload. Los lanzamientos de GitHub siguen siendo útiles para prelanzamientos y para actualizar una instalación por sideload ya existente.',
      is: '<strong>Svartifoss er líka á Google Play</strong> — ein færsla nær yfir bæði síma og úr, án handvirkrar uppsetningar. GitHub-útgáfur eru áfram gagnlegar fyrir forútgáfur og til að uppfæra fyrirliggjandi handvirka uppsetningu.'
    },
    'footer.releases': {
      'pt-BR': 'Lançamentos <svg class="icon"><use href="#i-open-new"/></svg>',
      es: 'Versiones <svg class="icon"><use href="#i-open-new"/></svg>',
      is: 'Útgáfur <svg class="icon"><use href="#i-open-new"/></svg>'
    },
    'footer.coffee': {
      'pt-BR': 'Buy Me a Coffee <svg class="icon"><use href="#i-open-new"/></svg>',
      es: 'Buy Me a Coffee <svg class="icon"><use href="#i-open-new"/></svg>',
      is: 'Buy Me a Coffee <svg class="icon"><use href="#i-open-new"/></svg>'
    },
    'footer.continuation': {
      'pt-BR': 'Uma continuação do <a href="https://github.com/matejdro/WearMusicCenter">Music Center for Wear</a> de matejdro',
      es: 'Una continuación de <a href="https://github.com/matejdro/WearMusicCenter">Music Center for Wear</a> de matejdro',
      is: 'Framhald af <a href="https://github.com/matejdro/WearMusicCenter">Music Center for Wear</a> eftir matejdro'
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
    },
    is: {
      title: 'Svartifoss — stjórnaðu tónlistinni af úlnliðnum',
      description: 'Svartifoss er ókeypis, opinn Wear OS-fylgihlutur sem færir afspilun, leit, biðröð, texta, lýsigögn og fullkomlega sérsniðna miðlunarstýringu á úlnliðinn þinn.'
    }
  };

  var selector = document.getElementById('language-select');
  var supported = { en: true, 'pt-BR': true, es: true, is: true };
  var browserLanguage = (navigator.language || 'en').toLowerCase();
  var language = browserLanguage.indexOf('pt') === 0 ? 'pt-BR' : (browserLanguage.indexOf('es') === 0 ? 'es' : (browserLanguage.indexOf('is') === 0 ? 'is' : 'en'));

  function t(source) {
    return language === 'en' ? source : (textCopy[source] && textCopy[source][language]) || source;
  }
  window.SvartifossI18n = { t: t };

  try {
    var stored = window.localStorage.getItem('svartifoss-language');
    if (stored && supported[stored]) language = stored;
  } catch (ignore) {}

  function translatePage(nextLanguage) {
    language = supported[nextLanguage] ? nextLanguage : 'en';

    document.documentElement.lang = language;
    if (selector) {
      selector.value = language;
      selector.setAttribute('aria-label', language === 'en' ? 'Language' : (language === 'is' ? 'Tungumál' : 'Idioma'));
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
      if (element.children.length || element.closest('[data-i18n-html], [data-i18n-dynamic], [data-stat]') || element.closest('svg')) return;
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

    document.querySelectorAll('[data-i18n-aria-label]').forEach(function (element) {
      element.setAttribute('aria-label', t(element.getAttribute('data-i18n-aria-label')));
    });

    var pageMeta = metadata[language];
    document.title = pageMeta.title;
    var description = document.querySelector('meta[name="description"]');
    if (description) description.setAttribute('content', pageMeta.description);
    document.querySelector('meta[property="og:title"]').setAttribute('content', pageMeta.title);
    document.querySelector('meta[property="og:description"]').setAttribute('content', pageMeta.description);

    var closeButton = document.querySelector('.lightbox-close');
    if (closeButton) closeButton.setAttribute('aria-label', language === 'en' ? 'Close enlarged image' : (language === 'pt-BR' ? 'Fechar imagem ampliada' : (language === 'is' ? 'Loka stækkaðri mynd' : 'Cerrar imagen ampliada')));

    try { window.localStorage.setItem('svartifoss-language', language); } catch (ignore) {}
    document.dispatchEvent(new CustomEvent('svartifoss:languagechange', { detail: { language: language } }));
  }

  if (selector) selector.addEventListener('change', function () { translatePage(selector.value); });
  translatePage(language);
})();
