# Modo Studio — estado de implementação

Fonte: `Modo_Studio_Especificacao_Completa.pdf`. Níveis de evidência como em `RASTREABILIDADE.md`
(1 escrito · 2 compila no CI · 3 testes unitários verdes no CI · 4 validado em dispositivo).

| ID | Requisito | Estado | Ficheiros | Notas |
|---|---|---|---|---|
| STUDIO-DET-01 | Algoritmo Traditional / Rigged / Hybrid (limiar 85 %) | IMPLEMENTADO (nível 1; CI por confirmar) | `domain/HybridDetection.kt`, `HybridDetectionTest.kt` | Comparação em inteiros, sem erro de arredondamento |
| STUDIO-DET-02 | Regras de refinamento (região parcial, desenho novo após bones, bones sem keyframes) | IMPLEMENTADO (nível 1) | idem | Região parcial e desenho novo vencem o limiar de 85 % (decisão tomada; ver abaixo) |
| STUDIO-DET-03 | Override manual e voltar ao automático | IMPLEMENTADO (nível 1) | idem | Falta o menu de toque longo no badge (UI) |
| STUDIO-DET-04 | Cache por layer | IMPLEMENTADO (nível 1) | idem | Recalcula só quando `LayerRigInput` muda |
| STUDIO-DET-05 | Badges verde/azul/roxo, tooltip, feedback de mudança | PARCIAL | idem | Lógica pronta; badge na timeline/painel de layers e vibração por ligar na UI |
| STUDIO-DET-06 | Execução em background e < 8 ms | PENDENTE | — | Falta ligar a eventos (criar/apagar bone, keyframe, colar, abrir projeto) e medir em dispositivo |
| STUDIO-BONES-01 | Bones, IK, rigging, mesh de deformação | PENDENTE | — | A detecção assume que estes dados existirão em cada frame (`FrameRigInfo`) |
| STUDIO-CAM3D-01 | Câmara 3D tipo prisma | PENDENTE | — | |
| STUDIO-SKY-01 | Céu 3D dia/noite | PENDENTE | — | |
| STUDIO-GRID-01 | Grade customizável | PENDENTE | — | |
| STUDIO-SEL-01 | Seleção avançada | PENDENTE | — | Detalhes por definir na especificação |
| APP-AUTH-01 | Login com Google | PENDENTE | — | Requer projeto Firebase/Google Cloud do autor |
| APP-ONB-01 | Onboarding (idioma + tutorial) | PENDENTE | — | |
| APP-REF-01 | Janela de referência | PENDENTE | — | |
| APP-BRUSH-01 | Definições avançadas, import/export e QR de pincéis | PENDENTE | — | A importação de pincel por imagem já existe na `claude3.1` |
| APP-UI-01 | Cor do app e fundo da Home | PENDENTE | — | |
| APP-ZOOM-01 | Zoom até 800 % | PENDENTE | — | Verificar o limite atual em `ViewTransform.kt` |
| APP-LASSO-01 | Laço: escala, perspetiva, malha, flip | EM IMPLEMENTAÇÃO | `domain/Lasso.kt`, `QuadTransform.kt` | Já existem na `claude3.1`; falta confirmar malha e flips |
| APP-ANIM-01 | Transições suaves das barras | PENDENTE | — | |

## Decisões e ambiguidades da especificação

- A secção 3.3 diz "Rigged se uso ≥ 85 %", mas a 3.4 diz que bones só numa região (ex.: só o braço)
  ou um desenho novo após bones forçam Hybrid. Implementado: **as regras de 3.4 têm prioridade**.
- "Bones existem mas nunca foram usados" e "nenhum keyframe de bone" são o mesmo caso → Traditional.
- Layer sem frames devolve Traditional (evita divisão por zero).
- `FrameRigInfo.drawnAfterBones` tem de ser preenchido pela UI/modelo quando um frame é desenhado
  depois de a layer ter bones.
