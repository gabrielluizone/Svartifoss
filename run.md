export JAVA_HOME=/home/gabrielskaftell/jdks/jdk-21.0.11+10 && export PATH=$JAVA_HOME/bin:$PATH &&# Comandos operacionais

Comandos para executar manualmente a partir da raiz do repositório.

## Preparação

Use JDK 21 e inicialize o submódulo antes do primeiro build:

```sh
export JAVA_HOME=/home/gabrielskaftell/jdks/jdk-21.0.11+10
export PATH="$JAVA_HOME/bin:$PATH"
git submodule update --init
```

O arquivo local.properties precisa apontar para o Android SDK. O arquivo mobile/google-services.json também é necessário para compilar o módulo mobile, mas não deve ser commitado.

Existem dois flavors de distribuição:

- github: build para sideload, com atualizador próprio. É o flavor padrão.
- play: build para a Play Store, sem atualizador próprio. O artefato de produção para a Play Store é .aab, não APK.

## Builds de debug

Build debug normal para instalar manualmente no telefone e no relógio:

```sh
./gradlew :mobile:assembleGithubDebug :wear:assembleGithubDebug
```

Build debug do flavor Play, útil para testar o conteúdo que será enviado à Play Store:

```sh
./gradlew :mobile:assemblePlayDebug :wear:assemblePlayDebug
```

Os APKs ficam em:

```sh
mobile/build/outputs/apk/github/debug/mobile-github-debug.apk
wear/build/outputs/apk/github/debug/wear-github-debug.apk
mobile/build/outputs/apk/play/debug/mobile-play-debug.apk
wear/build/outputs/apk/play/debug/wear-play-debug.apk
```

Para disponibilizar os arquivos na rede local, depois do build execute:

```sh
python3 serve_apk.py
```

O servidor usa a porta 8760 e também mostra links para os arquivos que ainda não foram compilados.

## Release para GitHub Releases

O release distribuído pelo GitHub é o flavor github em formato APK:

```sh
./gradlew :mobile:assembleGithubRelease :wear:assembleGithubRelease
```

Antes de gerar um release, confira versionCode e versionName em mobile/build.gradle e wear/build.gradle. Os dois módulos precisam continuar com o mesmo applicationId e a mesma chave de assinatura.

Os APKs gerados são:

```sh
mobile/build/outputs/apk/github/release/mobile-github-release.apk
wear/build/outputs/apk/github/release/wear-github-release.apk
```

O projeto lê a assinatura de keystore.properties quando esse arquivo existe. Ele não deve ser commitado. Sem ele, o build usa a chave de debug e não deve ser publicado como release para usuários existentes.

Criar o GitHub Release usando o GitHub CLI. O #nome mantém os nomes esperados pelo atualizador interno do aplicativo:

```sh
gh release create v4.0 \
  mobile/build/outputs/apk/github/release/mobile-github-release.apk#mobile-release.apk \
  wear/build/outputs/apk/github/release/wear-github-release.apk#wear-release.apk \
  --title "Svartifoss 4.0" \
  --notes-file CHANGELOG.md
```

Troque a tag, o título e as notas conforme a versão. Para publicar uma tag que já existe localmente, use git push origin v4.0 antes do comando acima.

## Release para a Play Store

### Testar o APK de release do flavor Play

Embora a Play Store receba bundles, o APK de release pode ser instalado manualmente para validação:

```sh
./gradlew :mobile:assemblePlayRelease :wear:assemblePlayRelease
```

### Gerar os bundles para upload

Gere os dois bundles assinados:

```sh
./gradlew :mobile:bundlePlayRelease :wear:bundlePlayRelease
```

Arquivos para o Play Console:

```sh
mobile/build/outputs/bundle/playRelease/mobile-play-release.aab
wear/build/outputs/bundle/playRelease/wear-play-release.aab
```

Envie os bundles para a mesma aplicação do Play Console. Telefone e Wear OS usam o mesmo applicationId e a mesma chave; não crie uma segunda aplicação com outro package name.

## Testes e verificações

Executar todos os testes JVM, incluindo os flavors:

```sh
export JAVA_HOME=/home/gabrielskaftell/jdks/jdk-21.0.11+10 && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew test
```

Executar apenas os testes do módulo mobile:

```sh
export JAVA_HOME=/home/gabrielskaftell/jdks/jdk-21.0.11+10 && export PATH=$JAVA_HOME/bin:$PATH && ./gradlew :mobile:testGithubDebugUnitTest :mobile:testPlayDebugUnitTest
```

Executar os testes do publisher de temas:

```sh
npm ci --prefix .github/community-theme-publisher
npm test --prefix .github/community-theme-publisher
```

Executar os testes das regras Firestore no emulador:

```sh
npm ci --prefix firebase
npm test --prefix firebase
```

## Firestore / Firebase

Publicar somente as regras Firestore no projeto de produção:

```sh
firebase deploy --only firestore:rules --project svartmusiccenter
```

Revise firestore.rules e execute os testes do emulador antes de publicar. Nunca coloque credenciais de service account no repositório ou dentro do APK.

## Community Store / temas comunitários

### Atualizar o workflow ou o publisher

Alterações em .github/workflows/publish-community-themes.yml, .github/community-theme-publisher/, firestore.rules ou nos contratos compartilhados devem ser commitadas e enviadas para o branch padrão:

```sh
git add .github/workflows/publish-community-themes.yml \
  .github/community-theme-publisher firestore.rules \
  common/src/main/assets/community-theme-constraints.json
git commit -m "chore: update community theme publishing"
git push origin master
```

O GitHub Actions usa a versão do workflow que está no branch padrão. Portanto, alterar o arquivo localmente não atualiza o workflow até o git push.

Consultar o workflow disponível:

```sh
gh workflow list
gh workflow view "Publish approved community themes"
```

### Publicar a Community Store completa

O workflow é o caminho recomendado para produção. Ele valida temas aprovados, publica os arquivos estáticos em docs/themes, atualiza contagens de likes, processa retiradas e exclusões de contas, faz commit/push no GitHub Pages e só depois finaliza os documentos no Firestore.

Executar manualmente:

```sh
gh workflow run "Publish approved community themes" --ref master
```

Forçar também a atualização das contagens de likes:

```sh
gh workflow run "Publish approved community themes" \
  --ref master \
  -f refresh_likes=true
```

Acompanhar a execução mais recente:

```sh
gh run watch "$(gh run list \
  --workflow publish-community-themes.yml \
  --limit 1 \
  --json databaseId \
  --jq '.[0].databaseId')"
```

O repositório precisa ter o secret FIREBASE_SERVICE_ACCOUNT configurado no GitHub. Sem esse secret, a publicação falha antes de ler ou finalizar os dados. O workflow também roda automaticamente pelo agendamento diário.

### Validar o publisher localmente sem publicar

O modo padrão faz leitura e validação, mas não grava arquivos nem altera o Firestore. Para executá-lo, a variável FIREBASE_SERVICE_ACCOUNT deve conter a service account autorizada:

```sh
node .github/community-theme-publisher/publisher.mjs
```

Não execute manualmente --publish em produção se o objetivo for atualizar a Community Store inteira: a publicação precisa do commit no GitHub antes da finalização no Firestore, e o workflow já coordena essas duas fases.

## Comandos úteis de diagnóstico

Listar tarefas de um módulo:

```sh
./gradlew :mobile:tasks --all
./gradlew :wear:tasks --all
```

Ver o estado do repositório antes de criar um release:

```sh
git status --short --branch
git log -5 --oneline
```
