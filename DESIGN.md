---
name: MailFlow Local
description: Sistema visual escuro e operacional para fluxos locais de e-mail.
colors:
  background: "#0d1421"
  surface: "#141e2e"
  surface-soft: "#1c283b"
  border: "#344158"
  text: "#f3f6ff"
  muted: "#adb9ce"
  accent: "#a5b1ff"
  accent-soft: "rgba(124, 140, 255, 0.13)"
  success: "#5de2a2"
typography:
  display:
    fontFamily: 'ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif'
    fontSize: "2rem"
    fontWeight: 700
    lineHeight: 1.2
    letterSpacing: "-0.025em"
  headline:
    fontFamily: 'ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif'
    fontSize: "1.35rem"
    fontWeight: 700
    lineHeight: 1.35
  title:
    fontFamily: 'ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif'
    fontSize: "1.1rem"
    fontWeight: 700
    lineHeight: 1.4
  body:
    fontFamily: 'ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif'
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.65
  label:
    fontFamily: 'ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif'
    fontSize: "1rem"
    fontWeight: 700
rounded:
  field: "9px"
  control: "10px"
  badge: "12px"
  panel: "14px"
  card: "16px"
  pill: "999px"
spacing:
  xs: "8px"
  sm: "12px"
  md: "16px"
  lg: "20px"
  xl: "24px"
  2xl: "28px"
  3xl: "32px"
  4xl: "36px"
components:
  button-primary:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.background}"
    rounded: "{rounded.control}"
    padding: "10px 16px"
  button-ghost:
    backgroundColor: "transparent"
    textColor: "{colors.text}"
    rounded: "{rounded.control}"
    padding: "10px 16px"
  input-field:
    backgroundColor: "#0c1221"
    textColor: "{colors.text}"
    rounded: "{rounded.field}"
    padding: "12px 13px"
  navigation-active:
    backgroundColor: "{colors.accent-soft}"
    textColor: "{colors.accent}"
    rounded: "{rounded.control}"
    padding: "11px 13px"
  content-card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    rounded: "{rounded.card}"
    padding: "22px"
  status-chip-ready:
    backgroundColor: "rgba(93, 226, 162, 0.13)"
    textColor: "{colors.success}"
    rounded: "{rounded.pill}"
    padding: "5px 8px"
---

# Design System: MailFlow Local

## Overview

**Creative North Star: "Área de Trabalho Local"**

“Área de Trabalho Local” descreve uma interface de uso pessoal, direta e previsível. O tema escuro sustenta sessões de composição, revisão e acompanhamento sem transformar o produto em uma peça promocional; a personalidade aparece na precisão dos estados, na linguagem comum e no uso contido do violeta.

A interface estende o sistema existente em modo Operate. A hierarquia mantém título e ação principal no topo, conteúdo e estados logo abaixo, com densidade moderada, alvos confortáveis e leitura clara em telas pequenas. Novas superfícies devem parecer parte do mesmo aplicativo, não uma identidade paralela.

**Key Characteristics:**

- Operação escura, legível e orientada a tarefas.
- Violeta reservado a ação, seleção e foco.
- Tipografia de sistema com hierarquia curta e direta.
- Superfícies separadas por tom e borda antes de sombra.

## Colors

A paleta combina fundos azul-marinho quase neutros, texto frio de alto contraste e um único acento violeta funcional. Os valores canônicos estão no frontmatter.

### Primary

- **Violeta de Ação:** aplicado a ações primárias, seleção, navegação ativa e foco; não funciona como decoração de grandes áreas.

### Neutral

- **Fundo Noturno:** base contínua da aplicação.
- **Superfície Azul-Escura:** painéis, cartões e navegação lateral.
- **Superfície Suave:** estados de hover e controles secundários.
- **Borda Fria:** separação estrutural entre regiões e linhas.
- **Texto Frio:** conteúdo principal e títulos.
- **Texto Atenuado:** ajuda, metadados e descrições.
- **Verde de Estado:** confirmação e disponibilidade, sem assumir papel de acento de marca.

### Named Rules

**The Violeta Funcional Rule.** Use o violeta para indicar ação, seleção ou foco; não o espalhe como ornamento.

## Typography

**Display Font:** pilha sans-serif do sistema.
**Body Font:** a mesma pilha sans-serif do sistema.
**Label/Mono Font:** rótulos usam a pilha do sistema; código usa Cascadia Code com fallback Consolas e monoespaçada.

**Character:** a tipografia é familiar, eficiente e deliberadamente não editorial. Peso e espaçamento criam hierarquia sem depender de uma fonte externa.

### Hierarchy

- **Display:** títulos de página compactos, com peso forte e espaçamento ligeiramente fechado.
- **Headline:** títulos de seções e painéis, um degrau abaixo do título de página.
- **Title:** títulos internos e itens de conteúdo.
- **Body:** texto funcional e explicações com entrelinha aberta; textos de ajuda costumam limitar-se a cerca de 68–72 caracteres.
- **Label:** rótulos de campo e controles em peso forte; cabeçalhos tabulares são menores e em maiúsculas.

### Named Rules

**The Sistema Primeiro Rule.** Preserve a pilha de sistema; não introduza fonte de marca ou contraste tipográfico ornamental sem uma decisão explícita de redesign.

## Layout

O shell operacional usa navegação lateral fixa de 250px e área principal centralizada. Contêineres chegam a 1120px; formulários estreitos chegam a 840px. A margem lateral é fluida e o ritmo recorrente se concentra em intervalos de 8px, 12px, 16px, 20px, 24px e 28px.

Em até 900px, a navegação torna-se uma barra superior recolhível e os contêineres reduzem as margens. Entre 720px e 620px, grades de duas colunas, cabeçalhos, listas e grupos de ação passam progressivamente para uma coluna. O conteúdo continua utilizável a partir de 320px.

## Elevation & Depth

O sistema operacional é plano por padrão: fundos tonais e bordas estruturam a profundidade. Existe uma sombra de cartão (`0 18px 50px rgba(0, 0, 0, 0.16)`) em módulos antigos do painel e um brilho verde (`0 0 14px rgba(93, 226, 162, 0.75)`) restrito ao indicador de estado local; ambos são exceções existentes, não uma linguagem a ampliar. Gradientes também permanecem limitados a esses módulos legados.

### Shadow Vocabulary

- **Elevação de Módulo Legado** (`0 18px 50px rgba(0, 0, 0, 0.16)`): apenas cartões antigos do painel.
- **Brilho de Estado Local** (`0 0 14px rgba(93, 226, 162, 0.75)`): apenas o ponto de disponibilidade local.

### Named Rules

**The Plano por Padrão Rule.** Novas superfícies operacionais usam contraste tonal e borda; não adicionam sombra, brilho ou gradiente por reflexo.

## Shapes

Campos usam cantos discretos (9px), controles e itens de navegação usam 10px, painéis ficam entre 12px e 14px e cartões principais usam 16px. Chips usam a forma de cápsula (999px). Bordas de 1px são o contorno recorrente; círculos e cápsulas ficam reservados a indicadores compactos.

## Components

### Buttons

- **Shape:** controles compactos, levemente arredondados e com altura mínima confortável.
- **Primary:** fundo violeta, texto no tom do fundo da aplicação e peso forte; aparece uma vez por decisão principal.
- **Hover / Focus:** o hover clareia o violeta; o foco visível usa contorno violeta de 2px com afastamento de 4px. Transições de cor duram 160ms com `ease-out`.
- **Ghost / Secondary:** o ghost é transparente com borda; o secundário usa a superfície suave. Ambos preservam o texto principal.

### Chips

- **Style:** cápsulas pequenas de estado, com fundos translúcidos e texto semântico.
- **State:** verde indica ativo ou pronto; rosa indica bloqueado, arquivado ou descadastrado; neutro cobre estados intermediários.

### Cards / Containers

- **Corner Style:** cartões principais usam cantos de 16px; painéis funcionais usam 14px.
- **Background:** superfície escura sólida sobre o fundo da aplicação.
- **Shadow Strategy:** sem sombra nas telas operacionais novas; consulte Elevation & Depth para as exceções legadas.
- **Border:** contorno estrutural de 1px.
- **Internal Padding:** 22px em cartões de conteúdo e até 28px em painéis de conexão.

### Inputs / Fields

- **Style:** fundo mais escuro que a superfície, borda fria, cantos de 9px e preenchimento de 12px por 13px.
- **Focus:** a borda muda para violeta e recebe um anel suave de 3px; o foco global continua visível por teclado.
- **Error / Disabled:** erros usam rosa; controles desabilitados reduzem a opacidade sem remover o contexto.

### Navigation

A navegação lateral agrupa tarefas primárias e mantém recursos secundários dentro de uma expansão explícita. Itens têm alvo mínimo de 44px; o estado ativo combina fundo violeta translúcido, texto violeta e peso forte. Em telas menores, a navegação torna-se uma região recolhível no topo.

### Composer Actions

Salvar rascunho e revisar mensagem aparecem juntos no fim do editor. Salvar permanece ghost e não exige o formulário completo; revisar recebe o tratamento primário e pode ficar desabilitado quando faltam pré-requisitos.

## Do's and Don'ts

### Do:

- Do preserve o tema escuro, a fonte de sistema e a orientação a tarefas.
- Do reserve o violeta para ação, seleção e foco.
- Do use estados vazios explicativos e ações com verbos claros.
- Do preserve alvos de pelo menos 44px e a reorganização para uma coluna em telas pequenas.

### Don't:

- Don't criar uma identidade paralela, uma metáfora grandiosa ou claims promocionais.
- Don't expandir gradientes, brilhos ou sombras legadas para novas superfícies operacionais.
- Don't usar cor como decoração quando borda, hierarquia e espaçamento resolvem a estrutura.
- Don't ocultar ações críticas ou estados de autorização em controles ambíguos.
