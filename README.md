# Nyra — IA pessoal local para Android

Nome de trabalho: **Nyra**. Desenvolvimento Android nativo a partir da auditoria do projeto IA-vtuber fornecido por Johnata.

**Estado: fundação em desenvolvimento; não é a V1 completa solicitada.** Consulte o status de compilação do commit e os relatórios de Actions; não confundir código presente com execução validada em celular.

## Código desta etapa

- Kotlin/Jetpack Compose: onboarding inicial, tema escuro violeta, navegação e drawer.
- Room: histórico, mensagens com estado de geração/interrupção, CRUD de memórias manuais e origem.
- Contexto reconstruído ao abrir histórico, seleção de mensagens recentes e busca lexical de memórias. Embeddings e resumos automáticos ainda não implementados.
- LiteRT-LM 0.16.0 em CPU: importação SAF de `.litertlm`, streaming, cancelamento nativo e liberação serializada. GGUF não suportado nesta etapa.
- Importação para armazenamento privado com arquivo parcial, espaço livre, SHA-256 calculado e rename ao concluir. Não há catálogo nem download retomável.
- Voz Android somente com voz portuguesa declarada offline pelo motor. Sem lip sync ou STT.
- Esta build não solicita INTERNET. Não há API key nem telemetria externa configurada.
- Perfil do avatar Esme e inspetor GLB reproduzível. Renderer ainda não integrado.

## Compilar

JDK 17, Gradle 8.11.1, Android SDK platform 36/build-tools 35.0.0. Abra no Android Studio ou execute, com Gradle instalado:

```sh
gradle :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Workflow `.github/workflows/android.yml` faz os mesmos passos e disponibiliza o APK **apenas após sucesso**. ABI filtrada para arm64-v8a; Android mínimo 12/API 31. Assinatura debug de desenvolvimento. Assinatura release ainda não configurada; nunca commitar keystores.

## Testar a build inicial

1. Importar modelo `.litertlm` compatível pela aba Modelos. Arquivos baixados pelo usuário podem ter licenças próprias. O cálculo do hash local não autentica a origem do modelo.
2. Selecionar modelo e enviar mensagem. Inicialização pode demorar; falhas aparecem na interface.
3. Interromper durante geração, reabrir o app e conferir recuperação do histórico.
4. Criar memória, desabilitar seu uso, corrigir ou excluir e conferir o comportamento.
5. Só comparar velocidade em condições equivalentes e no mesmo arquivo/modelo. Primeiro bloco de texto não é uma medição exata de primeiro token; blocos não são contados como tokens.

## Ainda pendente

Renderer VRM/MToon/spring bones; calibração, IK, engine de atuação e blackboard; lip sync; TTS próprio e STT; catálogo e downloads retomáveis; embeddings, consolidação, RAG e backup; PurpleCore/autotune com métricas reais; melhorias de UX/acessibilidade; testes longos e validação física no aparelho.

## Avatar

Esme 1.0, **soun.dhaptics**. Arquivo original `4024685333778527948.vrm.glb`, fornecido pelo usuário. Metadados incorporados proíbem uso comercial e exigem crédito; a URL de licença indica modificação/redistribuição permitidas. Não incluir em distribuição comercial. Binário não incluído nesta etapa; perfil em `app/src/main/assets/esme-profile.json` preserva metadados e URL de licença. Sem alegação de autoria da personagem.

## Documentação

- `docs/AUDIT.md`: inventário, bugs e limitações identificados.
- `docs/ARCHITECTURE.md`: decisões e marcos ponderados de aceitação.
- `docs/RESEARCH.md`: fontes e ensaios planejados.
- `docs/vrm-inspection.json`: contagens e checksum medidos do VRM.
- `tools/inspect_vrm.py`: inspeção estática reproduzível.

Histórico e memórias ficam no armazenamento privado Android. Não há backup automático do SO nesta build. Banco v1 exporta schema; futuras versões devem fornecer migrations sem apagar dados. Diagnósticos de inferência nativa são reduzidos ao nível ERROR; exportação sanitizada de logs ainda pendente.
