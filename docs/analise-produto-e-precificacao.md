# Svartifoss — análise consolidada de produto, mercado e precificação

**Data:** 6 de setembro de 2026
**Base da análise:** código em `master` (mobile `versionCode 61` / wear `161`, ambos `versionName 4.0`, ainda não lançado), o `CHANGELOG.md` completo (1.11 → 4.0), os 14 releases públicos no GitHub, `docs/play-store-migration-plan.md` e pesquisa externa de mercado e de política da Play Store.

> **Método.** Todos os números de produto abaixo foram contados no repositório (arrays de recursos, registries em Kotlin, arquivos de fonte), não copiados do README — que em alguns pontos já está desatualizado. Os números de mercado vêm das fontes listadas na seção 10. Onde a evidência é fraca, o texto diz que é fraca.

---

## 1. Sumário executivo

| | |
|---|---|
| **O que é** | Um sistema de dois aplicativos (telefone + relógio) que transforma um relógio Wear OS em um controle remoto profundamente configurável para o áudio que já toca no telefone. |
| **Maturidade técnica** | Alta e incomum para um projeto solo: 22 layouts de player, ~200 chaves de aparência com escopo por face, 45 idiomas, galeria pública de temas com moderação, testes de invariante em JVM cobrindo as decisões que já causaram bugs. |
| **Maturidade de produto** | Baixa. ~226 downloads do APK do telefone somando **todos** os releases, 9 estrelas no GitHub, 1 issue aberta, repositório público há 11 semanas. |
| **Concorrente real** | Não é outro app pago. É o controle de mídia **gratuito** embutido no Wear OS — que melhorou em 5.1 (retroceder/avançar/shuffle) e em 6 (controles persistentes no AOD, redesign Material 3 Expressive). |
| **Play Store** | Viável e a maior parte do trabalho de engenharia já está feita (flavors `github`/`play`, updater fisicamente ausente do artefato Play). Restam bloqueios de conta, assets e texto — nenhum deles técnico. |
| **Preço** | Faixa **US$ 3,99 – 6,99**; sugerido **US$ 4,99**; lançamento **US$ 2,99 por 30 dias**. |

**A tensão central desta análise.** O Svartifoss tem, de forma verificável, mais profundidade de personalização e de interoperabilidade de mídia do que qualquer alternativa que eu tenha encontrado no Wear OS — incluindo o app do qual ele é fork, que já saiu da Play Store. Isso justifica *posicionamento* premium. O que **não** existe ainda é qualquer prova de que alguém pagará por isso: o produto nunca foi exposto a um mercado. Precificar como se a superioridade técnica se convertesse automaticamente em disposição a pagar seria o erro mais caro possível aqui, porque o preço de um app sem avaliações não é lido como sinal de qualidade — é lido como risco.

---

## 2. Visão geral e propósito

O Svartifoss lê a *media session* ativa no telefone (qualquer app que publique uma sessão Android padrão) e a espelha no relógio: título, artista, capa, posição, fila e ações disponíveis. O relógio devolve comandos pelo Wearable Data Layer. Não toca áudio, não faz streaming, não exige conta para a função principal e não precisa de internet para ela.

**O modelo de dois apps é uma restrição de arquitetura, não uma escolha comercial.** O Data Layer só roteia mensagens entre um app de telefone e um app de relógio que compartilham `applicationId` **e** chave de assinatura. Daí decorrem três consequências que atravessam todo o resto do documento:

1. Uma única listagem paga na Play cobre os dois dispositivos — não há como cobrar separadamente sem quebrar a comunicação (tentativa já feita e revertida: o pacote `com.svartifoss.wrfell`).
2. O telefone é a autoridade de estado. O relógio pode agir localmente, mas qualquer preferência que precise sobreviver a uma reconexão volta ao telefone e é redistribuída.
3. A instalação exige duas etapas e a concessão de acesso a notificações — um custo de setup que o controle nativo do sistema não tem.

**O que o produto explicitamente não é:** um player, um watch face do Wear OS (as "faces" são layouts internos do app), uma integração de conta com Spotify/YouTube Music (os atalhos são links para apps instalados), nem um app com backend próprio.

---

## 3. Funcionalidades e diferenciais

### 3.1 O que realmente existe (contado no repositório)

| Área | Números verificados |
|---|---|
| Layouts de player ("faces") | **22** registrados em `ThemeAppearance.ALLOWED_BASE_FACES`; 7 arquivados → **15** visíveis no seletor padrão; 1 (`depth`) excluído da galeria pública |
| Estilos de always-on display | **17** |
| Tipografias no seletor | **142** opções; ~125 arquivos de fonte empacotados em cada módulo, com licença individual em `licenses/` |
| Tratamentos de artwork/fundo | **71** opções nos seletores; **87** casos em `PlayerBackgroundStyle`; fundo agora é uma pilha ordenada de até 8 camadas |
| Fundos de painel | **65** opções |
| Linhas de preferência | **329** (86 em `settings.xml` + 243 em `watch_face_settings.xml`) |
| Chaves de aparência com escopo por face | ~**200** |
| Idiomas | **45**, telefone e relógio, com seletor no app e integração ao seletor por app do Android 13+ |
| Classes de ação atribuíveis | **29** |
| Serviços de streaming reconhecidos em atalhos | **12** |
| Superfícies glanceable | 2 Tiles + 1 complication + uma `MediaSession` proxy no relógio |

### 3.2 Os diferenciais que sustentam preço

Quatro, e apenas quatro, resistem a uma leitura cética:

**1. O relógio como superfície de entrada mapeável.** Botões físicos (toque simples, duplo, longo), quatro quadrantes de tela, três direções de swipe, o toque central, coroa/bezel rotativo, até três mini-botões na tela, quatro slots de painel rápido e o gesto de pinça dupla — cada um atribuível a qualquer das 29 ações, e com **configurações separadas para "tocando" e "parado"**. Nada mais no Wear OS oferece isso; o controle nativo oferece zero mapeamento.

**2. Personalização visual em escala de app de watch face, aplicada a um controle remoto.** 22 faces × ~200 chaves com escopo por face, temas nomeados salváveis, e uma galeria pública moderada onde um tema é *dado* (chaves enumeradas com valores tipados, sem URL, caminho ou intent) — o que é exatamente o que torna hospedar conteúdo de usuário viável para um desenvolvedor solo. Isso é único no nicho.

**3. Interoperabilidade de mídia que vai além do transporte.** Navegação da biblioteca do player via `MediaBrowserService`, busca por voz/teclado, fila real com uma cadeia de fallback de capas (bitmap → URI local → extras → MediaStore → remoto), a escada de reprodução de deep link (sessão ativa → media browser → abertura visível) com verificação de que a reprodução realmente começou, espelhamento das ações reais da notificação de mídia, letras sincronizadas via LRCLIB e metadados técnicos do arquivo. O controle nativo entrega transporte e uma fila básica.

**4. Postura de privacidade defensável.** Local por padrão, sem conta para a função principal, cada caminho de rede é opcional e nomeado, política publicada e mantida. Para um app que exige acesso a notificações, isso não é um detalhe — é o que responde à objeção principal do comprador.

### 3.3 O que **não** sustenta preço (leitura crítica)

- **A função central é gratuita e nativa.** Tocar/pausar, pular e volume a partir do pulso já vêm em todo relógio Wear OS. O Svartifoss precisa vender o *excedente*, não a função.
- **Profundidade é invisível na vitrine.** 142 fontes e 87 tratamentos de fundo não aparecem em cinco screenshots. A maioria dos usuários nunca abre a aba de aparência; para eles, o app é "o controle de mídia com mais botões".
- **O imposto de setup é real e é cobrado antes de qualquer valor ser percebido.** Instalar dois apps, parear, conceder acesso a notificações — tudo isso acontece antes do primeiro benefício. É a etapa onde reembolsos e avaliações de uma estrela nascem.
- **A compatibilidade varia por player e isso não é ocultável.** O próprio README admite que like/shuffle/repeat/busca dependem de extensões que alguns apps não expõem. Para um app pago, "depende do seu player" é um obstáculo de conversão sério e honesto.
- **A galeria de temas não é um diferencial de compra.** É um recurso de retenção e de comunidade, com custo recorrente de Firebase e de moderação. Ela não convence ninguém a comprar; ela ajuda a manter quem já comprou.
- **Não há trial.** Apps pagos na Play não têm período de teste, e o único "teste" disponível — compilar ou baixar o APK do GitHub — só serve para quem já é entusiasta.

---

## 4. Evolução do aplicativo

### 4.1 Linha do tempo real

| Versão | Data | O que a versão significou |
|---|---|---|
| 1.11-beta | 20/06/2026 | Redesenho visual do relógio, seek, painel rápido, fila/histórico |
| 1.12 | 27/06/2026 | Modernização Wear (Phase 0/1), `WatchMediaSession` proxy, fila em Compose |
| 1.13 | 28/06/2026 | Design system e ajustes Wear |
| 2.0 | 04/07/2026 | **Rebrand para Svartifoss.** Sistema de ações amplo, 2 Tiles + complication, seek pela coroa, gestos generalizados |
| 2.1 / 2.1.1 | 09–10/07/2026 | Face Expressive, aba Watch no telefone, AOD configurável |
| 2.2 / 2.2.1 / 2.2.2 | 11–13/07/2026 | **Atualização sem cabo**: OTA do relógio por Bluetooth e, depois, autoatualização do telefone. Faces Vinyl e Poster (beta) |
| 3.0 | 23/07/2026 | **Ponto de inflexão.** Escopo de aparência por face, temas próprios salváveis, atalhos de streaming reconstruídos, +11 idiomas, backup único |
| 3.1-beta1/2 | 05–08/08/2026 | Harmonias de cor, tipografia por elemento, faces Carousel, biblioteca navegável no relógio |
| 3.1 | 19/08/2026 | Faces Chat/Note/Split, seletor de face **no relógio**, busca nas configurações, fila paginada |
| 3.2 | 24/08/2026 | **Letras sincronizadas**, face Metadata, face Verse, correção da deriva de posição de reprodução, +26 idiomas |
| 4.0 | não lançado | **Galeria comunitária de temas** (submissão, moderação, likes, denúncia, exclusão de conta), pilha de fundo em camadas, faces Artist/Ribbon/Frame/MatejDro, split de flavors `github`/`play`, +5 idiomas |

### 4.2 O que a evolução mostra — e o que ela esconde

**Mostra:** uma trajetória coerente em três atos. **1.11–2.0** consertou e modernizou o que veio do fork. **2.0–2.2.2** resolveu distribuição (atualizar relógio e telefone sem cabo, o que é a única razão pela qual o modelo sideload sobreviveu). **3.0–4.0** transformou o app de "controle remoto configurável" em "plataforma de aparência", com escopo por face, temas portáteis e finalmente uma galeria pública. A curva de valor é claramente ascendente e o 4.0 é o primeiro release com um recurso *social*.

**Esconde três coisas que importam para a decisão de preço:**

1. **Velocidade sem exposição.** Quatorze releases em nove semanas é excepcional, mas cada versão teve dias — não meses — de uso real. As seções "Fixed" do changelog são longas e, na maior parte, descrevem regressões encontradas pelo próprio autor ou por testes de invariante, não por usuários. O app é *maduro em decisões* e *imaturo em campo*.
2. **Nada roda os testes além do desenvolvedor.** O único workflow do GitHub Actions publica temas da comunidade; ele não compila nem testa o app. Não há teste instrumentado. Para um produto pago, isso é um risco operacional a assumir conscientemente.
3. **A dívida de documentação pública já apareceu.** O contador de faces no `docs/index.html` ainda diz 21 enquanto o registro tem 22; o README afirma um limite de 3 submissões por 24h enquanto as regras do Firestore permitem 10. São detalhes, mas a listagem da Play será escrita a partir desses mesmos textos.

---

## 5. Posicionamento frente à concorrência

### 5.1 O concorrente real: o controle de mídia nativo do Wear OS

Este é o comparativo que decide o preço, porque é o único que 100% dos compradores potenciais já têm instalado e gratuito.

| Dimensão | Wear OS nativo (Media Controls) | Svartifoss |
|---|---|---|
| Custo | Gratuito, pré-instalado | Pago (proposto) + setup em dois dispositivos |
| Transporte básico | Sim; desde 5.1 inclui retroceder, avançar e shuffle | Sim |
| Persistência no AOD | Sim, a partir do Wear OS 6 (rollout em curso nos Pixel Watch) | Sim, com 17 estilos de AOD configuráveis |
| Aparência | Fixa (Material 3 Expressive, cores derivadas do sistema) | 22 layouts, ~200 chaves por face, temas salvos e galeria pública |
| Mapeamento de entrada | Nenhum | Botões físicos, quadrantes, swipes, coroa, mini-botões, painel rápido, pinça dupla — por estado de reprodução |
| Fila | Básica, quando o player expõe | Fila real paginada com capas, fallback para histórico, toque verificado |
| Busca / biblioteca | Não | Busca por voz/teclado e navegação da biblioteca via `MediaBrowserService` |
| Letras | Não | Sim (LRCLIB), com face dedicada |
| Atalhos de playlist | Não | 12 serviços, com escada de reprodução em background |
| Tiles / complication | Parcial, por app | 2 Tiles + complication próprios |

**Leitura honesta:** o nativo cobre o caso de uso de 90% das pessoas e ficou melhor exatamente na área onde o Svartifoss se destacava (AOD e controles estendidos). O Svartifoss não compete em "controlar música"; compete em **"controlar música sem olhar para o relógio, do jeito que eu quero, com a aparência que eu escolhi"**. Esse é um mercado real, mas é uma fração pequena da base Wear OS.

### 5.2 Concorrentes de terceiros

| App | Preço | Situação |
|---|---|---|
| **Music Boss for Wear OS** (Reboot's Ramblings) | ~US$ 2 | O concorrente direto mais próximo. 10 mil+ instalações, **3,6 estrelas**, herança de Wear OS 1.x/2.0. Gestos e Tasker, mas nada perto da profundidade de aparência do Svartifoss. |
| **Music Boss Deluxe** | assinatura | Versão multiplataforma (Pebble/Garmin/Wear) com biblioteca navegável; o desenvolvedor migrou para assinatura. |
| Wear Gesture Control / Gesture Launcher | grátis / freemium | Lançadores de gesto genéricos; mídia é um caso de uso entre muitos. |
| Apps dos próprios players (Spotify, YT Music) | grátis | Melhor integração com o próprio serviço, zero personalização, zero mapeamento de entrada. |

**O dado mais útil de toda esta seção:** o incumbente do nicho, com oito anos de vitrine na Play, está em **10 mil+ instalações a US$ 2 e 3,6 estrelas**. Isso é o teto observável da demanda por "controle de mídia de terceiros no Wear OS" — não um piso. Qualquer projeção que assuma dezenas de milhares de vendas contradiz a única evidência disponível.

### 5.3 O app de origem

O `Music Center for Wear` de matejdro — do qual o Svartifoss é fork — era **gratuito**, parou na versão 1.6.0 (2017) e sua página na Play Store hoje retorna 404. Isso é simultaneamente uma oportunidade (o nicho ficou órfão) e um alerta (o nicho não sustentou nem um app gratuito e ativo).

### 5.4 Onde o Svartifoss ganha e onde perde

**Ganha:** profundidade de personalização (sem concorrente), mapeamento de entrada (sem concorrente), interoperabilidade de mídia (só o Music Boss Deluxe chega perto), alcance linguístico (45 idiomas é atípico até para apps comerciais), e postura de privacidade documentada.

**Perde:** descoberta (zero), prova social (zero avaliações), custo de setup, dependência do telefone, ausência de trial, dependência de um único desenvolvedor, e — estruturalmente — o fato de que a Google melhora a linha de base de graça a cada versão do Wear OS.

### 5.5 O risco estrutural a nomear

Se o Wear OS 7 ou 8 adicionar controles de mídia customizáveis ou mapeamento de botões nativo, a proposta de valor do Svartifoss encolhe para "aparência e interoperabilidade avançada". A trajetória 5.1 → 6 mostra a Google avançando exatamente nessa direção. Isso não invalida o produto, mas **argumenta contra assinatura** e a favor de compra única: é mais honesto vender o que existe hoje do que prometer relevância recorrente sobre uma base que a plataforma pode absorver.

---

## 6. Publicação na Google Play Store

### 6.1 O que já está pronto

- **Split de flavors implementado e verificado.** O artefato `play` não contém o autoatualizador nem `REQUEST_INSTALL_PACKAGES` — não há nada para "desativar" na revisão, o código simplesmente não está lá. Isso resolve o risco de *Device and Network Abuse*, que é o motivo número um de rejeição para apps que se atualizam fora da Play. Garantido por `FlavorSelfUpdateIsolationTest`.
- **Target API em conformidade.** Desde 31/08/2026 novos apps e atualizações precisam mirar API 36; apps Wear OS têm exceção e precisam de API 35+. O projeto está em **36 (telefone)** e **35 (relógio)**. Conforme.
- **Requisito de 64 bits (prazo 15/09/2026).** O projeto não declara `abiFilters` nem NDK e não traz código nativo próprio; as bibliotecas Google/Firebase já são 64-bit. Conforme por construção.
- **Requisito de UGC atendido.** A denúncia de temas dentro do app entrou no 4.0, com o backend de moderação e a fila de moderadores — que é o que a política de conteúdo gerado por usuário exige.
- **Política de privacidade publicada** e ligada a partir do app.

### 6.2 Bloqueios reais (nenhum é técnico)

1. **Teste fechado de 12 testadores por 14 dias.** Contas *pessoais* de desenvolvedor criadas após 13/11/2023 precisam de 12 testadores continuamente inscritos por 14 dias antes de obter acesso à produção — e desde 2026 a Google também avalia se esses testadores **realmente usaram** o app. *Verificar a data e o tipo da conta do Play Console antes de qualquer outra coisa*: se ela for anterior a novembro de 2023 ou for uma conta de organização, este bloqueio não existe. Se existir, ele adiciona no mínimo duas semanas ao cronograma e exige recrutar 12 pessoas com relógio Wear OS — o que, com a base atual, é o item mais difícil desta lista inteira.
2. **Declaração de acesso a notificações.** É permissão restrita: exige declaração no Console, justificativa de funcionalidade central e, na prática, vídeo de demonstração. A boa notícia é concreta: os casos de uso permitidos incluem explicitamente **apps que retransmitem notificações para dispositivos vestíveis** e apps que mostram notificações em uma interface alternativa. O Svartifoss cai exatamente aí. O vídeo deve mostrar o fluxo inteiro: conceder acesso → o relógio passar a mostrar a faixa → os botões da notificação de mídia aparecerem no painel rápido.
3. **Assets de vitrine inexistentes ou obsoletos.** Não existe *feature graphic* (1024×500, obrigatório). Os 5 screenshots de telefone em `fastlane/` são de 11/07/2026 — anteriores ao 3.0, portanto mostram um produto que não existe mais. Não há screenshots de relógio no formato exigido: **mínimo 384×384, proporção 1:1, sem moldura de relógio e sem texto/fundo adicionado**. O repositório tem 65 imagens em `docs/images/`, mas elas são fotos e mockups de divulgação — precisam ser recapturadas nas regras da Play.
4. **Texto de listagem desatualizado.** `docs/play-console-store-listing.md` descreve o conjunto de recursos do 2.x, diz que o app é gratuito e a versão Wear ainda cita o pacote abandonado `com.svartifoss.wrfell`. Reescrever a partir do `CHANGELOG.md` do 4.0.
5. **Declaração de serviço em primeiro plano** (`mediaPlayback`) e formulário de **Data Safety** cobrindo Crashlytics, Analytics, FCM e as escritas no Firestore da galeria.

### 6.3 Riscos de política, ordenados por probabilidade

| Risco | Probabilidade | Mitigação |
|---|---|---|
| Rejeição/atraso na declaração de acesso a notificações | Média | Vídeo mostrando funcionalidade central; citar o caso de uso "retransmitir para vestível"; divulgação proeminente dentro do app (já existe o diálogo + banner) |
| Bloqueio pelo teste fechado de 12 testadores | Média-alta se a conta for pessoal e recente | Verificar a conta agora; se aplicável, recrutar testadores antes de tudo |
| Data Safety inconsistente com o comportamento real | Média | O app tem 8 caminhos de rede nomeados no `CLAUDE.md`; transcrever um a um em vez de preencher por memória |
| Conteúdo gerado por usuário (temas) | Baixa | Denúncia, moderação e remoção já implementados no 4.0 |
| Autoatualização | Muito baixa | Fisicamente ausente do flavor `play` |
| APK gratuito no GitHub visto como subcotação | Baixa | GitHub Releases não é uma loja concorrente; ainda assim, **não** transformar "grátis no GitHub" na manchete da listagem |

### 6.4 Assinatura — a decisão irreversível

Fornecer o `release.keystore` existente como **chave de assinatura do app** na Play (não apenas chave de upload). Isso mantém as instalações da Play com a mesma assinatura das atuais por sideload, de modo que:

- quem já usa atualiza no lugar, sem desinstalar (o que apagaria a configuração);
- releases futuros no GitHub continuam compatíveis com instalações da Play;
- telefone e relógio dentro do mesmo app compartilham a chave, e o Data Layer continua pareando.

O custo é assumir o risco de rotação da chave que a Google carregaria. Para um app com base instalada e um canal paralelo de sideload, a continuidade de assinatura vale mais. **Se a Google gerar a chave, todo usuário atual precisa desinstalar e reinstalar** — o que, com a base atual de algumas dezenas de pessoas, é gerenciável hoje e deixará de ser depois.

### 6.5 Ordem recomendada

1. Verificar tipo e data da conta do Play Console (define o cronograma inteiro).
2. Se necessário, recrutar os 12 testadores — começar por aqui, é o caminho crítico.
3. Fechar o 4.0 e publicá-lo primeiro no GitHub, ganhando 2–4 semanas de uso real antes da vitrine paga.
4. Recapturar screenshots (telefone + relógio nas regras da Play) e produzir o feature graphic.
5. Reescrever a listagem, Data Safety, declaração de notificações e de serviço em primeiro plano.
6. Configurar Play App Signing com a chave existente.
7. `bundlePlayRelease` dos dois módulos, submeter, aguardar a revisão de permissão restrita.
8. Passar a listagem para pago **no dia da publicação** — um app publicado como gratuito não pode ser convertido em pago depois.

> **Atenção a este último item — é a decisão irreversível desta lista.** A Play permite baixar o preço de um app pago e permite torná-lo gratuito, mas **um app publicado como gratuito nunca pode ser convertido em pago**: a única saída é criar uma listagem nova com outro `applicationId`, o que aqui significaria quebrar o Data Layer e abandonar toda a base instalada. A decisão de cobrar precisa estar tomada antes do primeiro release de produção.

---

## 7. Análise de precificação (USD)

### 7.1 Âncoras reais de mercado

| Referência | Preço | Por que é relevante |
|---|---|---|
| Music Boss for Wear OS | ~US$ 2 | Concorrente direto; define o piso percebido do nicho |
| Navigation Pro (Wear) | ~US$ 2 | Utilitário Wear pago conhecido |
| Tasker | ~US$ 3,80 | Ferramenta de poder, escopo enorme, compra única |
| Poweramp (unlocker) | US$ 3,99 | Áudio + personalização profunda |
| Nova Launcher Prime | US$ 4,99 | **A âncora mais próxima**: personalização profunda, compra única, promoções agressivas periódicas |

Utilitários pagos de nicho no Wear OS vivem entre US$ 1,99 e US$ 5,99. Acima disso, sem marca, a conversão despenca.

### 7.2 Modelo de cobrança

O plano do repositório já fixou **um app pago único** (`com.svartifoss.snfell`), uma compra cobrindo os dois dispositivos. Mantenho essa recomendação, com uma ressalva honesta:

> **Ressalva.** Tecnicamente, o modelo com melhor conversão aqui seria *gratuito com desbloqueio único in-app*, porque o maior risco de compra do Svartifoss é "funciona com o meu player e o meu relógio?" — pergunta que o comprador não consegue responder antes de pagar, e cuja resposta errada vira reembolso e avaliação de uma estrela. Um app gratuito que espelha a reprodução e cobra pela camada de aparência/temas eliminaria essa objeção. Isso **não** contradiz a GPLv3, mas contradiz a promessa atual do README ("nenhum recurso é bloqueado por pagamento") e adiciona trabalho de faturamento. Se a escolha for compra única — e há bons motivos para ela, incluindo simplicidade e o alinhamento com a licença — então o preço de lançamento precisa fazer o trabalho que um trial faria.

**Não recomendo assinatura.** O produto não tem custo recorrente proporcional por usuário (a galeria é o único, e é pequena), e a plataforma pode absorver parte do valor a qualquer momento — cobrar recorrentemente por isso seria difícil de defender.

### 7.3 Faixa, preço final e promoção

| | Valor | Justificativa |
|---|---|---|
| **Faixa recomendada** | **US$ 3,99 – 6,99** | O piso fica acima do patamar de commodity de US$ 2 (Music Boss/Navigation Pro), sinalizando outra categoria. O teto é o limite onde um utilitário Wear sem avaliações ainda converte. |
| **Preço final sugerido** | **US$ 4,99** | Empata com Nova Launcher Prime, a âncora funcionalmente mais próxima (personalização profunda, compra única). É 2,5× o concorrente direto, o que é defensável pelo salto de escopo, e é um preço que o comprador não precisa pensar duas vezes para pagar. |
| **Promoção de lançamento** | **US$ 2,99 por 30 dias** (–40%) | Fica acima dos US$ 2 do Music Boss (não redefine a âncora para baixo permanentemente), mas é visivelmente um desconto. O objetivo desta janela **não é receita — é obter as primeiras 30–50 avaliações**, sem as quais nenhum preço funciona. |
| **Piso emergencial** | US$ 1,99 por 14 dias | Só se os 30 dias iniciais produzirem menos de ~50 unidades. Usar uma vez, com prazo anunciado. |

**Por que não US$ 6,99 desde o início:** é defensável pela profundidade — e indefensável pela ausência de prova social. Um app com 0 avaliações a US$ 6,99 num nicho cujo incumbente cobra US$ 2 não é lido como premium; é lido como caro. O caminho para US$ 6,99 existe (7.6), mas ele passa por avaliações, não por argumentos.

### 7.4 Preço regional

Não usar a conversão automática cega. Definir manualmente pelo menos:

| Mercado | Padrão | Lançamento |
|---|---|---|
| EUA / Europa Ocidental | US$ 4,99 / € 4,99 | US$ 2,99 |
| **Brasil** | **R$ 24,90** | **R$ 14,90** |
| Índia, Indonésia, Turquia, Filipinas | equivalente a US$ 1,99–2,49 | — |

O app está traduzido para 45 idiomas, muitos deles de mercados sensíveis a preço. Cobrar o equivalente a US$ 4,99 na Índia anula o investimento em localização — que é um dos ativos mais subestimados do projeto.

### 7.5 Projeção realista de receita (ano 1)

Taxa de serviço da Play: 15% efetivos para o caso mais comum aqui (desde 30/06/2026, novas instalações em EUA/EEE/Reino Unido pagam 10% de serviço + 5% de taxa de faturamento). Líquido por unidade: **US$ 4,24** a US$ 4,99; **US$ 2,54** a US$ 2,99.

| Cenário | Premissa | Unidades/ano | Receita líquida |
|---|---|---|---|
| **Pessimista** | Sem cobertura de imprensa; descoberta só orgânica | ~120 | ~US$ 500 |
| **Base** | Uma menção em Reddit/r/WearOS ou um blog Android de médio porte | ~500 | ~US$ 2.100 |
| **Otimista** | Cobertura em Android Police/9to5Google + boca a boca no nicho | ~2.000 | ~US$ 8.500 |

**Interpretação crítica.** Mesmo o cenário otimista não financia desenvolvimento em tempo integral, e o cenário base mal cobre uma fração do custo de oportunidade. Some-se a isso o custo recorrente do Firebase (Auth, Firestore, Storage da galeria) e a carga de moderação, que crescem com o sucesso. **A função do preço aqui não é receita — é legitimidade, um canal de atualização automática e um ciclo de feedback com usuários que se comprometeram.** Se o objetivo primário fosse alcance, o modelo correto seria gratuito com doação; se é sustentabilidade e um sinal de qualidade, é o preço acima.

### 7.6 Quando aumentar o preço

Subir para **US$ 5,99** quando houver ≥100 avaliações com média ≥4,3 e taxa de reembolso <5%. Subir para **US$ 6,99** apenas se, além disso, houver cobertura editorial ou um crescimento orgânico sustentado por dois trimestres. Nunca subir e voltar: a Play mostra o histórico de preço em sites de rastreamento, e oscilação lê como instabilidade.

Promoções: no máximo duas por ano (uma sazonal, uma de aniversário), sempre com prazo, nunca abaixo de US$ 1,99. O padrão do Nova Launcher — preço cheio o ano todo e uma queda anual profunda — é o modelo a copiar.

---

## 8. Recomendações priorizadas

**Antes de pensar em preço:**

1. Verificar tipo/data da conta do Play Console — define se o cronograma é de 2 ou de 6 semanas.
2. Lançar o 4.0 no GitHub primeiro. Ele é o maior release do projeto e nunca foi exposto a ninguém. Publicar direto numa vitrine paga um binário que ninguém usou é o cenário de pior risco.
3. Corrigir as inconsistências públicas antes que virem texto de listagem: contador de faces em `docs/index.html` (21 → 22), limite de submissões no README (3 → 10), e os textos que ainda dizem "não está na Play Store".

**Para a vitrine:**

4. Recapturar todos os screenshots no 4.0, seguindo as regras da Play para Wear (384×384, 1:1, sem moldura).
5. Escrever a descrição em torno das três perguntas do comprador, nesta ordem: *funciona com o meu app de música?*, *o que eu ganho além do que já tenho?*, *quanto trabalho é instalar?*. A vitrine atual responde a terceira por último e as duas primeiras mal.
6. Ser explícito sobre compatibilidade na própria descrição. Ocultar a variação por player converte melhor e devolve reembolso e uma estrela.

**Para o preço:**

7. Publicar já como pago, a US$ 2,99 (janela de 30 dias), com preço regular de US$ 4,99 configurado.
8. Definir preços regionais manualmente para Brasil, Índia, Indonésia, Turquia e Filipinas.
9. Usar os códigos promocionais gratuitos da Play para semear os primeiros avaliadores — incluindo os 12 testadores, se o teste fechado for exigido.

**Estrutural:**

10. Adicionar um workflow de CI que rode `./gradlew test` e as duas suítes Node. Hoje nada além do desenvolvedor executa os testes; com usuários pagantes, isso deixa de ser aceitável.

---

## 9. O que invalidaria esta análise

- **Se a conta do Play Console for antiga/organizacional**, o cronograma encurta drasticamente e o lançamento pode acontecer em duas semanas.
- **Se o Wear OS 7 trouxer mapeamento de entrada nativo**, a seção 5 muda de figura e o preço sugerido cai para a faixa de US$ 2–3.
- **Se a galeria comunitária ganhar tração real**, o efeito de rede vira o argumento de venda principal e sustenta US$ 6,99 mais cedo do que a seção 7.6 prevê.
- **Se surgir um concorrente pago moderno**, o piso de US$ 2 do Music Boss deixa de ser a âncora e a faixa inteira se desloca.
- **Os números de download do GitHub subestimam a base real** (não contam quem compilou nem quem usa o atualizador embutido, que baixa via API e pode não incrementar o contador de assets da mesma forma). A ordem de grandeza — dezenas, não milhares — é sólida; o número exato não é.

---

## 10. Fontes

**Internas (repositório, em 06/09/2026):** `CHANGELOG.md`, `README.md`, `docs/play-store-migration-plan.md`, `docs/play-console-store-listing.md`, `docs/play-console-wear-store-listing.md`, `CLAUDE.md`, `svartifoss/01-product/*`, `firestore.rules`, `mobile/build.gradle`, `wear/build.gradle`, arrays de recursos em `mobile/src/main/res/values/`, e a API de releases do GitHub.

**Externas:**

- [Wear OS 6 traz controles de mídia ao always-on display — 9to5Google](https://9to5google.com/2025/05/29/wear-os-6-aod/)
- [Wear OS 6 supercharging the AOD — Android Police](https://www.androidpolice.com/wear-os-6-is-supercharging-the-always-on-display-with-media-controls-and-more/)
- [Explore features | Wear OS 6 — Android Developers](https://developer.android.com/training/wearables/versions/6/features)
- [Wear OS app quality — Android Developers](https://developer.android.com/docs/quality-guidelines/wear-app-quality)
- [Target API level requirements — Play Console Help](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)
- [App testing requirements for new personal developer accounts — Play Console Help](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en)
- [Service fees — Play Console Help](https://support.google.com/googleplay/android-developer/answer/112622?hl=en)
- [Set up your app's prices (gratuito → pago é irreversível) — Play Console Help](https://support.google.com/googleplay/android-developer/answer/6334373?hl=en)
- [Expanded billing choice and lower fees on Google Play — Android Developers Blog](https://android-developers.googleblog.com/2026/06/play-expanded-billing.html)
- [Permissions and APIs that Access Sensitive Information — Play Console Help](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en)
- [Music Boss for Wear OS — Google Play](https://play.google.com/store/apps/details?id=ca.rebootsramblings.musicbossforwear&gl=US)
- [Music Boss for Wear OS — estatísticas AppBrain](https://www.appbrain.com/app/music-boss-for-android-wear-control-your-music/ca.rebootsramblings.musicbossforwear)
- [WearMusicCenter (app de origem) — GitHub](https://github.com/matejdro/WearMusicCenter)
- [Nova Launcher Prime — preço e histórico de promoções, Android Authority](https://www.androidauthority.com/nova-lifetime-deal-before-it-ends-3641474/)
- [Wear OS Smartwatch Market — Verified Market Reports](https://www.verifiedmarketreports.com/product/wear-os-smartwatch-market/)
- [Bring one-handed gestures to your Wear OS app — Android Developers Blog](https://android-developers.googleblog.com/2026/08/one-handed-gestures-wear-os.html)
