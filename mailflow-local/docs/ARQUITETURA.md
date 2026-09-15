# Arquitetura

## Visão geral

O MailFlow Local é um monólito modular. Essa forma mantém a instalação simples e, ao mesmo tempo, evita que contatos, mensagens e entrega se tornem um único bloco difícil de manter.

```text
Navegador em localhost
        │
        ▼
Spring MVC + Thymeleaf
        │
        ▼
Serviços de aplicação e domínio
    ├── Contatos e grupos
    ├── Templates e mensagens
    ├── Agendamentos e sequências
    └── Fila e histórico
        │
        ├──────────────► SMTP do provedor
        │
        ▼
PostgreSQL + Flyway
```

## Pacotes

| Pacote | Responsabilidade |
|---|---|
| `contact` | Agenda, grupos, tags e importação |
| `template` | Conteúdo reutilizável e variáveis |
| `message` | Rascunhos, destinatários e anexos |
| `scheduling` | Datas, recorrências e política de atraso |
| `sequence` | Etapas e inscrições de follow-up |
| `delivery` | Fila, SMTP, limites e retentativas |
| `history` | Tentativas, pesquisa e exportação |
| `settings` | Contas SMTP, preferências e backup |
| `dashboard` | Consultas de resumo para a interface |

Os pacotes ainda não existentes representam incrementos planejados, não código ausente acidentalmente.

## Decisões técnicas

### Java e Spring Boot

Java concentra as regras de negócio, validações, transações, telas e integrações. Spring Boot fornece configuração, servidor local e componentes compatíveis sem exigir um servidor de aplicação separado.

### PostgreSQL e SQL

PostgreSQL é a fonte de verdade. SQL é usado para:

- Estrutura e integridade dos dados.
- Índices e consultas de relatório.
- Bloqueios necessários ao processamento concorrente da fila.
- Migrações versionadas pelo Flyway.

Procedures e triggers devem ser usados apenas quando a garantia precisa existir no próprio banco. Fluxos de negócio permanecem em Java.

### JPA e SQL explícito

JPA atende cadastros e agregados comuns. Consultas de painel, fila e relatórios podem utilizar `JdbcClient` ou SQL nativo quando isso deixar o comportamento mais claro e eficiente.

### Agendamentos

Quartz identifica quando um evento deve ocorrer. As tabelas de domínio continuam sendo a fonte de verdade para mensagem, destinatário e estado. Isso evita guardar toda a regra de negócio dentro do mapa de dados de um job Quartz.

O template começa com o JobStore em memória para não criar tarefas incompletas. Antes de habilitar automações reais, deverão ser adicionadas as tabelas Quartz por Flyway e a configuração alterada para JobStore JDBC.

### Entrega SMTP

O domínio depende da interface `EmailGateway`; `SmtpEmailGateway` é apenas uma implementação. Isso permite testes sem envio e futuras integrações sem espalhar código SMTP pelo sistema.

### Identificadores e horários

- Identificadores usam UUID para facilitar importação, backup e evolução.
- Instantes são armazenados como `timestamp with time zone` em UTC.
- O fuso de apresentação e cálculo fica nas configurações do aplicativo.

## Modelo de entrega

```text
Mensagem confirmada
    │
    ├── Destinatário bloqueado? ──► Ignorar e registrar
    │
    ▼
Criar delivery_job com idempotency_key
    │
    ▼
Worker reserva o trabalho
    │
    ├── SMTP aceitou ──► SENT + tentativa ACCEPTED
    │
    ├── Erro temporário ──► RETRY + nova data
    │
    └── Erro permanente ──► FAILED
```

O worker deverá reservar trabalhos com transação e bloqueio de linha, processando apenas uma quantidade limitada por ciclo.

## Credenciais

A coluna `secret_reference` guarda um blob cifrado pelo DPAPI. Somente o mesmo usuário do Windows no mesmo contexto consegue decifrá-lo. A senha nunca é devolvida para a interface. As variáveis de ambiente continuam disponíveis apenas como alternativa de desenvolvimento para o adaptador global.

## Limites da execução local

- O computador precisa estar ligado para enviar no horário exato.
- Confirmação de aceitação SMTP não equivale a entrega na caixa de entrada.
- Bounces e respostas exigem consulta IMAP ou webhooks do provedor.
- Aberturas e cliques exigem uma URL pública; não pertencem ao núcleo local.

## Evolução futura

Se algum dia houver uma edição hospedada, os limites entre módulos, UUIDs, migrações e a interface de entrega permitem evolução. Multiusuário, isolamento entre clientes e cobrança não serão antecipados no código atual.
