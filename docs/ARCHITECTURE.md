# Arquitetura e aceitação

## Decisão inicial

Kotlin/Compose, ViewModel e Coroutines; Room com schema exportado, sem destructive migration. DataStore para preferências. Runtime isolado atrás de InferenceBackend. Operações de inferência, banco e importação fora da thread principal. Modelo importado pelo Storage Access Framework, copiado atomicamente para armazenamento privado. UI nunca oferece sucesso antes de concluir.

Primeiro backend: LiteRT-LM Android 0.16.0, versão verificada no repositório oficial. Suporta API Kotlin, backend CPU/GPU e cancelamento nativo. Não afirmar suporte GGUF: segundo backend llama.cpp/JNI é marco separado. Não habilitar NPU ou especulação sem medição.

Renderer candidato: Filament Android é nativo e suporta glTF, mas não equivale a suporte completo VRM/MToon/spring bones. Integração exige adaptador VRM e validação no avatar. Alternativa three-vrm é mais completa para VRM, mas JavaScript/WebGL exige superfície dedicada; não converter UI principal em WebView. Decisão final condicionada ao teste visual e consumo real.

## Marcos ponderados (100 pontos)

| Marco | Peso | Evidência de aceite |
|---|---:|---|
| Auditoria e arquitetura | 5 | Inventário, bugs, perfil VRM, plano e restrições documentados |
| Fundação Android | 10 | Build arm64 + instalação/abertura, rotação e restauração |
| Modelos e inferência | 20 | Instalar/retomar/checksum, chat streaming offline, cancelamento, benchmark |
| Histórico e memória | 15 | CRUD, continuidade, migrations, recuperação, busca e embeddings locais |
| Avatar e atuação | 25 | Render correto, dedos/IK/colisão, timeline/blackboard, testes visuais |
| Voz e sincronização | 10 | TTS/STT offline, alinhamento e interrupção medidos |
| PurpleCore | 10 | Autotune e comparação baseline, RAM/temperatura em aparelho real |
| Polimento/release | 5 | Acessibilidade, backup, testes longos, release assinado |

Progresso só é creditado após evidência; implementação parcial fica explicitamente marcada. Primeiro marco vale 5%, não o percentual de linhas de código escrito. Critérios completos do usuário continuam válidos; extras depois da V1 não tornam a V1 dispensável.

## Contratos corporais

LLM emite intenção semântica validada; executor publica estado observado com id/geração e instante monotônico. Intended não vira completed sem confirmação do renderer. Interrupção invalida callbacks antigos. Gesto tem preparation/stroke/hold/retraction e âncora na amostra de áudio, com recuperação contínua. Nenhum movimento aprovado somente porque é matematicamente suave. Avaliar colisão, alcance e limites na pose real retargeted.

## Pesquisa consultada

- https://github.com/google-ai-edge/LiteRT-LM/releases/tag/v0.16.0
- https://github.com/google-ai-edge/LiteRT-LM/blob/v0.16.0/kotlin/java/com/google/ai/edge/litertlm/Engine.kt
- https://github.com/google-ai-edge/LiteRT-LM/blob/v0.16.0/kotlin/java/com/google/ai/edge/litertlm/Conversation.kt
- https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
- https://github.com/google/filament
- https://developer.android.com/build/releases/agp-8-9-0-release-notes

Pesquisa científica de gesto, voz e modelos recomendados ainda pendente; não apresentar esta lista como revisão acadêmica completa.
