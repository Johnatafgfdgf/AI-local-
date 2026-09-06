# Auditoria inicial — 2026-09-06

Inspeção estática de todos os arquivos de aplicação, configuração, manifesto de dependências e metadados do ZIP. Não executada a aplicação web. Arquivos originais não alterados.

## Inventário

- React 19/Vite 6, canvas Three.js, three-vrm 3.5: importação de VRM e animação, orbit/zoom, seis humores, controles voz/autonomia.
- Gemini: uma chamada JSON por resposta; somente seis mensagens recentes; sem streaming.
- Web Speech API: voz do navegador; não garante execução offline.
- Quota fictícia local 1500/dia; não representa limites reais do provedor.
- Histórico, avatar e personalidade apenas em React state. Recarregar perde as conversas. localStorage guarda somente quota.
- Nenhum projeto Android, banco versionado, STT, embeddings, downloader de modelos, runtime local, teste ou benchmark.

## Problemas observados no código

1. `vite.config.ts` injeta GEMINI_API_KEY no bundle cliente. Nova aplicação não herda esse mecanismo.
2. `VTuberPrototype.tsx` alterna boca a cada 150 ms; sem relação com fonemas ou áudio.
3. Upload BVH vira blob URL e perde extensão; `endsWith('.bvh')` falha e seleciona o loader GLTF.
4. VRM carrega assincronamente sem invalidar callbacks antigos. Troca/unmount pode anexar modelo obsoleto e vazar recursos.
5. Animações têm cleanup com referências de estado antigas; mixer e ações podem permanecer vivos.
6. Texturas não são integralmente descartadas; frustum culling é desativado para tudo.
7. `vrm.update` precede mudanças dos ossos normalizados; alterações aparecem apenas na atualização seguinte. A pose neutra não é aplicada antes da primeira exposição do modelo.
8. BVH usa nomes presumidos e escala fixa 0.1; não há calibração nem correção de bind pose.
9. Respiração desloca modelo inteiro; Float acrescenta flutuação. Lerp fixo depende do FPS. Piscar é seno periódico.
10. Detecção de quota procura 429 na resposta, mas o serviço captura erros e os transforma em texto genérico.
11. Autonomia não verifica fala em andamento; TTS e timers podem competir. Modo mudo simula fala por duração de texto.
12. Falta validação runtime do JSON do modelo e cancelamento de requisições.
13. Imports e funções sem uso: Heart, Zap, Volume2 em partes; getAvatarFace/getAnimation; useMemo, scene/camera. Express/better-sqlite3/dotenv não têm uso no código de aplicação inspecionado. Vite duplicado em dependencies/devDependencies.
14. Assets de animação e ambiente são URLs externas, incompatíveis com garantia offline.

## Reutilização

Reutilizar conceitos de mapeamento humanoide, camadas faciais, upload de avatar e estados de interação. Não portar o gerenciamento de quota, prompt de afeição/ciúmes nem timers de boca. A nova identidade é uma assistente virtual transparente; energia/emoção são estados de atuação, não sentimentos reais.

## VRM fornecido

Esme 1.0 / soun.dhaptics. 25,614,772 bytes. VRM 0.x, 151 nós, 3 meshes, 3 skins, 18 materiais, 34 texturas. 55 ossos humanoides, incluindo 30 articulações de dedos. Sem animações GLTF. Presets neutral/a/i/u/e/o/blink/blink_l/blink_r/angry/fun/joy/sorrow/unknown.

Metadados e URL incorporada proíbem uso comercial e exigem crédito; indicam modificação e redistribuição permitidas. A página da licença não pôde ser carregada nesta sessão; interpretação limitada aos metadados. Perfil JSON extraído separadamente. Limites articulares específicos e qualidade visual ainda não calibrados: não inferir da existência dos ossos que os gestos estão prontos.

## Ambiente

JDK 17 presente. Android SDK, Gradle, NDK, adb e emulador não encontrados. Tentativas diretas de acesso ao GitHub e ao download do SDK não concluíram. API autenticada do GitHub funciona. Compilação será tentada em GitHub Actions, sem alegar validação em celular físico.
