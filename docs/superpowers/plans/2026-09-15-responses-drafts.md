# Respostas e rascunhos Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Executado no checkout autorizado pelo usuário; revisões independentes foram usadas no fechamento e nenhuma publicação externa foi realizada.

**Goal:** receber respostas relacionadas aos envios, guardar rascunhos persistentes e tornar a navegação mais clara.

**Architecture:** rascunhos editáveis têm tabela própria e versão; mensagens confirmadas mantêm o fluxo existente. IMAP somente leitura usa autorização separada, cursor e reserva persistidos; Message-ID por tarefa associa as respostas aos envios.

**Tech Stack:** Java 25, Spring Boot, Thymeleaf, PostgreSQL, Jakarta Mail e Jsoup.

**Spec:** `docs/superpowers/specs/2026-09-15-responses-drafts-design.md`.

## Global Constraints

- Instalação local em 127.0.0.1; nenhum teste usa a caixa ou banco pessoal.
- Sem expiração automática dos rascunhos; exclusão manual autenticada e CSRF.
- Gmail IMAP em imap.gmail.com:993; TLS e identidade do servidor obrigatórios.
- Não importar caixa inteira nem executar conteúdo recebido.
- Nenhum log registra senha, corpo ou cabeçalhos recebidos.

## Task 1: Rascunhos persistentes

Files: criar `draft/SavedDraftService.java`, `draft/SavedDraftController.java`, `templates/drafts/list.html`; modificar `DispatchController.java`, `templates/delivery/form.html`; migração V5.

Interfaces: `SavedDraftService.create(DispatchForm): UUID`, `get(UUID): Draft`, `update(UUID,long,DispatchForm): long`, `delete(UUID): void`, `list(int): List<Draft>`. `Draft.form()` reabre o formulário. A versão inicial é zero.

- [x] Escrever teste de salvamento incompleto, edição, versão conflitante, isolamento e exclusão.
- [x] Executar `mvn -Dtest=WorkspaceFeaturesTest test`, confirmar ausência da funcionalidade. Asserções centrais: `assertThat(jobs).isZero()` e `assertThatThrownBy(() -> drafts.update(id,0,changed)).isInstanceOf(IllegalArgumentException.class)` após uma atualização bem-sucedida.
- [x] Implementar tabela de rascunhos, controle de versão e rotas. Botão save não exige campos de envio completos; review valida no serviço existente.
- [x] Executar novamente o teste; conferir que revisar uma cópia não apaga o rascunho salvo.

## Task 2: Identificação de respostas

Files: modificar `EmailMessage.java`, `DispatchWorker.java`, `SecureSmtpGateway.java`; criar `inbox/ReplyHeader.java` e `inbox/ImapReplyReader.java`.

Interfaces: `EmailMessage` ganha `messageId`, preservando o construtor de cinco argumentos; `ReplyHeader` extrai UUIDs conhecidos dos cabeçalhos. `ImapReplyReader.read(SmtpAccount,String,long,long,Predicate<ReplyHeader>): Batch` retorna cursor e respostas relacionadas.

- [x] Escrever testes de MIME malicioso, limites, cabeçalhos e captura SMTP do Message-ID. Exemplo literal de ID: `<123e4567-e89b-12d3-a456-426614174000@mailflow.local>`.
- [x] Executar `mvn -Dtest=ReplyContentTest,SecureGatewayTlsTest test`, confirmar falha esperada.
- [x] Implementar leitura TLS read-only e extração limitada de texto; ignorar anexos e conteúdo externo.
- [x] Executar os testes; confirmar bytes/header produzidos e ausência de HTML executável na leitura.

## Task 3: Sincronização opcional e aba Respostas

Files: criar `InboxService.java`, `InboxController.java`, `InboxPolling.java`, `templates/inbox/list.html`; acrescentar tabelas à V5.

Interfaces: `configure(UUID,boolean)`, `sync(UUID,UUID)`, `related(UUID,UUID,ReplyHeader)`, `list(int)`; autorização pelo workspace e conta; reserva com token valida finalização.

- [x] Escrever testes de opt-in, correlação remetente/conta/Message-ID, deduplicação, cursor e reserva concorrente; usar leitor sintético como fronteira de rede.
- [x] Executar `mvn -Dtest=WorkspaceFeaturesTest test`, observar falhas esperadas.
- [x] Implementar armazenamento, sincronização, polling e rotas protegidas.
- [x] Executar teste; erro de rede não deve avançar cursor nem expor erro externo.

## Task 4: Interface, documentação e regressão

Files: `static/css/app.css`, `templates/fragments/navigation.html`, `dashboard.html`, novas listas, formulário e `README.md`.

- [x] Ler craft-floor do Impeccable e melhorar controles, ritmo, estados vazios, responsividade e navegação existente.
- [x] Executar testes HTTP das páginas com dados sintéticos; conferir Salvar rascunho e Respostas, CSRF e saída HTML escapada.
- [x] Compilar com `mvn package` e somar resultados XML do Surefire.
- [x] Inspecionar desktop/mobile em preview sintético; registrar eventuais ferramentas indisponíveis.
- [x] Atualizar README com uso das respostas, rascunhos, portas e caminhos de Java/HTML/CSS/SQL; revisar diff e documentação.

## Fechamento

- Verificação final em 18/09/2026: 99 testes, 0 falhas, 0 erros e 0 ignorados.
- A prévia visual utilizou somente dados sintéticos na porta 8083; nenhum Gmail, destinatário ou banco pessoal foi acessado.
- A migração V5 foi validada em PostgreSQL temporário. Em uma instalação existente, o usuário deve fazer backup antes de iniciar o novo JAR.
- A validação real do Gmail permanece uma ação explícita do proprietário dentro da aplicação e não é fixture de teste.
