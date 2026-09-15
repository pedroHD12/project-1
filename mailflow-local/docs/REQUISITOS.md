# Requisitos do MailFlow Local

Este documento transforma o planejamento do produto em requisitos verificáveis. O foco atual é uso pessoal e local; assinatura, cobrança, planos e múltiplos clientes não fazem parte do escopo.

## Objetivo

Permitir que uma pessoa organize contatos, escreva mensagens e automatize envios legítimos por suas próprias contas SMTP, mantendo dados e administração no computador local.

## Perfis de uso contemplados

- Mensagem individual imediata.
- Mensagem individual ou em grupo agendada.
- Lembretes periódicos.
- Aniversários e datas comemorativas.
- Avisos e cobranças pessoais.
- Follow-ups em etapas.
- Comunicados personalizados para grupos.
- Mensagens com anexos.

## Requisitos funcionais

### RF-01 — Contas SMTP

- Cadastrar mais de uma conta remetente.
- Validar host, porta, criptografia e credenciais.
- Enviar uma mensagem de teste antes de habilitar a conta.
- Selecionar a conta usada por mensagem.
- Desabilitar uma conta sem apagar seu histórico.
- Cifrar a credencial com DPAPI antes de gravar o blob protegido no banco.

### RF-02 — Contatos

- Cadastrar, editar, pesquisar, bloquear e excluir contatos.
- Importar e exportar CSV com pré-visualização dos dados.
- Impedir duplicidade pelo endereço normalizado.
- Organizar contatos em grupos e tags.
- Manter nome, empresa, aniversário, observações e campos personalizados.
- Exibir o histórico de mensagens de cada contato.

### RF-03 — Templates

- Criar templates em texto e HTML.
- Definir assunto e conteúdo.
- Usar variáveis como `{{nome}}`, `{{empresa}}` e `{{data}}`.
- Avisar quando uma variável não possuir valor.
- Pré-visualizar o conteúdo preenchido para um contato.
- Duplicar, arquivar e versionar modelos.

### RF-04 — Mensagens

- Criar rascunhos e salvar automaticamente.
- Selecionar contatos, grupos ou endereços avulsos.
- Evitar destinatários repetidos.
- Adicionar anexos com tamanho e tipo validados.
- Enviar uma cópia de teste.
- Exibir um resumo e solicitar confirmação antes de envios coletivos.

### RF-05 — Agendamentos

- Enviar imediatamente ou em data e hora específicas.
- Criar recorrências diárias, semanais, mensais, anuais e personalizadas.
- Respeitar o fuso horário configurado.
- Pausar, retomar, cancelar e reagendar.
- Definir uma data final para recorrências.
- Em caso de computador desligado, permitir as políticas: enviar depois, ignorar ou pedir confirmação.

### RF-06 — Sequências

- Organizar vários templates em etapas ordenadas.
- Definir espera em minutos, horas, dias ou semanas.
- Restringir envios a uma janela de horário.
- Inscrever e remover contatos manualmente.
- Pausar uma sequência inteira ou apenas um contato.
- Registrar a etapa atual e o motivo do encerramento.

### RF-07 — Fila e entrega

- Gerar um trabalho independente para cada destinatário.
- Usar uma chave de idempotência para impedir duplicidade.
- Aplicar limites por minuto, hora e dia.
- Repetir falhas temporárias com intervalos crescentes.
- Não repetir falhas permanentes sem ação do usuário.
- Permitir pausa global e cancelamento seguro.
- Consultar a lista de bloqueio imediatamente antes da entrega.

### RF-08 — Histórico

- Registrar mensagem, destinatário, horário e resultado.
- Registrar cada tentativa sem salvar credenciais.
- Pesquisar por período, assunto, destinatário e estado.
- Reenfileirar uma falha de maneira consciente.
- Exportar os resultados.

### RF-09 — Backup

- Criar backup manual e automático do PostgreSQL.
- Salvar anexos e configurações não secretas.
- Validar o arquivo antes de uma restauração.
- Permitir definir retenção e diretório de backup.

### RF-10 — Operação local

- Executar apenas em `localhost` por padrão.
- Iniciar opcionalmente junto com o Windows.
- Exibir diagnóstico do banco, agendador e SMTP.
- Continuar preservando os agendamentos após reinicialização.

## Requisitos não funcionais

### RNF-01 — Segurança

- Não armazenar senha SMTP em texto simples no PostgreSQL; usar DPAPI do usuário atual do Windows.
- Não registrar segredos nos logs.
- Validar todo conteúdo recebido pela interface.
- Sanitizar HTML antes de permitir edição visual ou importação.
- Exigir autenticação local antes de habilitar acesso fora de `127.0.0.1`.

### RNF-02 — Confiabilidade

- Operações de criação da fila devem ser transacionais.
- O reprocessamento de um trabalho não pode duplicar um envio já confirmado.
- Datas devem ser persistidas em UTC e apresentadas no fuso configurado.
- Falhas de um destinatário não podem interromper os demais.

### RNF-03 — Manutenibilidade

- Alterações no banco devem ser feitas exclusivamente por migrações Flyway.
- Regras de negócio não devem ficar em controllers ou templates HTML.
- Integrações externas devem implementar interfaces do domínio.
- Módulos não devem acessar tabelas de outros módulos sem serviço ou contrato explícito.

### RNF-04 — Privacidade

- Os dados permanecem locais por padrão.
- Telemetria deve ser desativada por padrão.
- Exportação e remoção de contatos devem ser possíveis.
- Rastreamento de abertura ou clique não será implementado sem consentimento e infraestrutura apropriada.

### RNF-05 — Desempenho inicial

- O painel deve abrir em até dois segundos em uma máquina doméstica típica.
- Importações devem processar arquivos grandes em lotes.
- Listas devem usar paginação no servidor.
- A fila não deve carregar todos os trabalhos pendentes na memória.

## Fora do escopo atual

- Assinaturas e pagamentos.
- Planos comerciais e quotas de cobrança.
- Múltiplos clientes ou organizações.
- Aplicação pública na internet.
- Rastreamento público de abertura e cliques.
- Servidores SMTP próprios.
- Disparo de mensagens não solicitadas.

## Definição geral de pronto

Uma funcionalidade só é considerada pronta quando possui validação, migração quando necessária, testes automatizados, tratamento de erros, mensagem compreensível para o usuário e atualização da documentação.
