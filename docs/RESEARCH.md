# Pesquisa inicial aplicada — 2026-09-06

Esta é uma seleção inicial, não uma revisão completa dos temas pedidos.

| Fonte primária | Implicação para implementação/validação |
|---|---|
| Flash e Hogan, coordenação de movimentos do braço, 1985: https://pubmed.ncbi.nlm.nih.gov/4020415/ | Suavidade de alcance tem base experimental; usar trajetória de mão suave como componente, sem concluir que interpolação suave garante atuação humana. |
| Admoni e Scassellati, revisão de olhar social, 2017: https://scazlab.yale.edu/sites/default/files/files/273-2310-1-PB.pdf | Separar atenção, função comunicativa e comportamento ocular; resultados de HRI não são regras deterministas sobre o estado emocional do usuário. |
| GENEA Challenge 2026, preprint: https://arxiv.org/abs/2608.10839 | Avaliar naturalidade e adequação à fala separadamente. Medidas numéricas não substituem avaliação visual humana. O trabalho é preprint. |
| LiteRT-LM Android Kotlin: https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md | Inicializar fora da UI; manter gestão explícita de recursos. Interface InferenceBackend permite segundo runtime posteriormente. |
| Gemma 4 em LiteRT-LM: https://developers.google.com/edge/litert-lm/models/gemma-4 | Candidato para avaliação futura; não existe recomendação de RAM/velocidade desta aplicação sem medir o arquivo e o dispositivo. |
| Sherpa-ONNX TTS: https://k2-fsa.github.io/sherpa/onnx/tts/index.html | Candidato para voz própria instalável; revisar cada voz/modelo e licença, latência e alignment antes de substituir o fallback Android. |

## Ensaios a executar no motor corporal

1. Comparar desempenho com fala correta e fala trocada para separar sincronismo e semântica.
2. Contagem parametrizada de intervalos, não apenas uma animação de 1 a 10. Variar mão inicial, velocidade, interrupção e pose inicial.
3. Consultar estado durante preparation, stroke, hold, recovery. Renderer confirma execução; plano sozinho não prova execução.
4. Registrar colisões e rejeições. Nenhuma classe GOLD/GREEN sem inspeção e verificação das restrições.
5. Avaliar gestos com e sem áudio; fluência motora e pertinência são dimensões distintas.
6. Lip sync medido contra amostras de áudio. Callback de palavra não é marcação de fonema.

Nenhum desses ensaios visuais foi executado nesta etapa.
