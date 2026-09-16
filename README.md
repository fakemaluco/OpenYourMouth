# OpenYourMouth

Mod Fabric exclusivamente client-side para Minecraft 1.21.11 e 26.2. Cada versão tem seu próprio JAR. Mensagens normais enviadas pelo jogador entram em uma fila local de TTS e, por padrão, continuam sendo enviadas imediatamente ao chat. O áudio é misturado ao fluxo de voz espacial do próprio jogador usando Simple Voice Chat ou Plasmo Voice.

## Baixar

Baixe o JAR correspondente à sua versão do Minecraft na [página de releases](https://github.com/fakemaluco/openyourmouth/releases/latest) e coloque-o na pasta `mods` do cliente. Instale também Fabric API e Simple Voice Chat ou Plasmo Voice para a mesma versão do jogo. Não instale os dois JARs do OpenYourMouth ao mesmo tempo.

## Requisitos

- Java 21 para Minecraft 1.21.11; Java 25 para Minecraft 26.2
- Fabric Loader 0.19.3 ou posterior compatível
- Para 1.21.11: Fabric API 0.141.6+1.21.11 e Simple Voice Chat 2.6.22 ou Plasmo Voice 2.1.14
- Para 26.2: Fabric API 0.160.0+26.2 e Simple Voice Chat 2.6.23 ou Plasmo Voice 2.1.17

O servidor e os ouvintes precisam somente do mesmo voice chat normalmente usado pelo servidor. Eles não precisam instalar OpenYourMouth.

## Créditos

- Fakiz_

## Configuração

O mod começa desligado. A primeira ativação exige consentimento explícito para processar o texto pelo provedor escolhido.

- Simple Voice Chat: abra o menu principal do voice chat e use o botão **TTS**.
- Plasmo Voice: use **Add-ons → OpenYourMouth** para ações rápidas. Ao selecionar Fish, a aba nativa **Fish TTS** aparece com voz, velocidade, perfil e teste.
- Arquivo local: `config/openyourmouth/config.json`.

Para interromper somente a fala atual, use o comando client-side `/tts skip`, o botão **Pular** no menu, ou envie o gatilho configurado (por padrão `-skip`). O gatilho é interceptado localmente e não é enviado ao chat público; as próximas mensagens da fila são preservadas.

Em **Enviar mensagens ao chat**, é possível impedir que as mensagens normais apareçam no chat público. Desativada essa opção, o texto é interceptado no cliente e somente o TTS é transmitido. `/tts chat`, `/tts chat on` e `/tts chat off` alteram a mesma configuração. Comandos e mensagens recebidas continuam fora do TTS.

- **Google Tradutor (experimental):** não requer chave; o serviço escolhe a voz.
- **Microsoft Edge TTS (experimental):** não requer chave; oferece catálogo de vozes filtrado pelo idioma, velocidade e tom. A implementação Java segue o protocolo do projeto `rany2/edge-tts`.
- **Fish Audio / Fish TTS:** usa a API oficial ou um endpoint Fish Speech compatível. Chave, voz, modelo-base e latência ficam visíveis de imediato; o mesmo painel expande endpoint, ID bruto e parâmetros de síntese pelo toggle persistente **Mostrar configurações avançadas**. Os modelos `s2-pro`, `s1`, `s2.1-pro`, `s2.1-pro-free` e um ID personalizado podem ser escolhidos diretamente.
- **VOICEVOX:** conecta a um VOICEVOX Engine já em execução. O padrão é `http://127.0.0.1:50021`; a tela importa personagens/estilos de `/speakers` e oferece velocidade, pitch e entonação. O mod não instala nem inicia o VOICEVOX.

O Fish possui um catálogo local persistente e pesquisável. As vozes sincronizadas da conta e as adicionadas manualmente por `reference_id` aparecem em grupos identificados; falhas de rede não apagam o catálogo anterior. A sincronização percorre até cinco páginas de 100 modelos e ocorre uma vez por sessão, além da atualização manual.

A tela geral abre no modo simples. Backend e comando personalizado de skip ficam recolhidos em **Configurações avançadas**. O volume usa uma escala de 0% a 200%, com padrão de 50%; a velocidade Edge usa 50% a 200%, com padrão de 100%. O pitch permanece disponível somente no arquivo local de configuração.

A opção **Ignorar acentos na fala** remove diacríticos de letras latinas (`á` → `a`, `ç` → `c`) antes de enviar o texto ao provider. Ela não modifica a mensagem exibida ou enviada ao chat e preserva marcas de outros sistemas de escrita.

**Ignorar maiúsculas** converte apenas o texto sintetizado para minúsculas. As duas opções alteram somente a entrada do TTS e nunca editam a mensagem enviada ao servidor.

O Fish Audio pode cobrar conforme o modelo e a conta escolhidos. A chave é salva somente em `config/openyourmouth/config.json`, fica mascarada nas interfaces e nunca é registrada nos logs. Em uma instalação própria, altere `fishEndpoint`; a chave pode ficar vazia caso o servidor não exija autenticação. Se os dois voice chats estiverem instalados, selecione explicitamente `SIMPLE_VOICE_CHAT` ou `PLASMO_VOICE` no campo `backend`.

## Compilar e executar

```powershell
# Java 21 ou superior: port para 1.21.11
.\gradlew.bat build

# Java 25: port para 26.2
.\gradlew.bat build '-PtargetMinecraft=26.2'

# Para testar cada voice chat, adicione -PtargetMinecraft=26.2 quando necessário:
.\gradlew.bat runSimpleVoiceChat -PvoicechatTest=simple
.\gradlew.bat runPlasmoVoice -PvoicechatTest=plasmo
```

Os JARs de distribuição são `build/libs/openyourmouth-1.6.1+mc1.21.11.jar` e `build/libs/openyourmouth-1.6.1+mc26.2.jar`. A dependência de decodificação MP3 é aninhada em cada JAR; os dois voice chats permanecem opcionais e não são empacotados. As fontes de 26.2 ficam em `src/modern/java`, pois essa versão mudou APIs de interface, comandos e chat.

## Limitações conhecidas

- Os endpoints usados pelo Google Tradutor e Microsoft Edge TTS não possuem contrato público e podem mudar ou aplicar rate limit sem aviso. Não há fallback silencioso entre provedores.
- VOICEVOX precisa estar em execução antes da síntese. Endpoints VOICEVOX fora da máquina local recebem o texto pela rede e são destacados com um aviso.
- Somente mensagens públicas normais enviadas pelo jogador são sintetizadas. Comandos, mensagens recebidas e mensagens do sistema não são processados.
