# Roadmap de desenvolvimento

Cada incremento deve terminar utilizável e testado antes do seguinte. A ordem prioriza segurança: primeiro visualizar e validar, depois agendar e finalmente automatizar.

## Incremento 0 — Template técnico

Estado: **criado**.

- Projeto Spring Boot e PostgreSQL.
- Migração inicial do banco.
- Entidades de contato e template.
- Adaptador SMTP desacoplado.
- Painel local responsivo.
- Documentação de requisitos e arquitetura.

Critério de saída: projeto compila, testes passam e o painel abre com PostgreSQL vazio.

## Incremento 1 — Contas SMTP e diagnóstico

Estado: **implementado em código; pendente validação com um provedor real**.

- Cadastro de contas sem persistir a senha.
- Proteção da credencial com DPAPI do Windows.
- Validação de conexão.
- Envio de teste somente para o endereço confirmado pelo usuário.
- Diagnóstico de banco, SMTP e fila.

Critério de saída: conta pode ser validada sem expor segredo em tela, banco ou log.

## Incremento 2 — Contatos

Estado: **em andamento**. CRUD, pesquisa, bloqueio e exclusão estão implementados; grupos, tags e CSV permanecem pendentes.

- CRUD de contatos.
- Grupos, tags e campos personalizados.
- Bloqueio e normalização de endereço.
- Importação CSV com mapeamento, prévia e relatório de erros.
- Exportação CSV.

Critério de saída: importação repetida não cria duplicidades silenciosas.

## Incremento 3 — Templates e composição

Estado: **em andamento**. CRUD, arquivamento e validação de conteúdo estão implementados; pré-visualização, sanitização e anexos permanecem pendentes.

- CRUD de templates.
- Texto, HTML e variáveis.
- Pré-visualização por contato.
- Sanitização de HTML.
- Rascunhos e anexos.
- Mensagem de teste.

Critério de saída: variáveis ausentes são exibidas antes da confirmação.

## Incremento 4 — Fila segura

- Criação transacional de trabalhos.
- Chaves de idempotência.
- Worker com reserva de linha.
- Limites de velocidade.
- Retentativas e classificação de erros.
- Pausa e cancelamento.

Critério de saída: reiniciar o processo durante um trabalho não duplica envio confirmado.

## Incremento 5 — Agendamentos

- Envio único.
- Recorrências.
- Fuso horário e horário permitido.
- Políticas para execução perdida.
- JobStore JDBC do Quartz gerenciado por Flyway.

Critério de saída: reiniciar computador e aplicação preserva o próximo horário corretamente.

## Incremento 6 — Sequências

- Editor de etapas.
- Inscrição de contatos.
- Esperas e janelas de envio.
- Pausa por contato.
- Encerramento e auditoria.

Critério de saída: cada contato avança no máximo uma vez por etapa.

## Incremento 7 — Histórico e relatórios

- Pesquisa e filtros.
- Detalhes das tentativas.
- Reenvio consciente.
- Indicadores no painel.
- Exportação.

Critério de saída: todo trabalho possui uma cadeia explicável desde a criação até o resultado.

## Incremento 8 — Operação cotidiana

- Backup e restauração.
- Inicialização com Windows.
- Instalador ou pacote de execução.
- Atualização segura.
- Política de retenção de logs.
- Manual do usuário.

Critério de saída: instalação, backup, restauração e atualização são testados em um computador limpo.

## Backlog posterior

- Consulta IMAP para respostas e devoluções.
- Editor visual avançado.
- Regras condicionais em sequências.
- Integração com calendário.
- API local autenticada.
- Temas e acessibilidade ampliada.

## Funcionalidades deliberadamente adiadas

- Cobrança e assinatura.
- Contas de clientes.
- Hospedagem pública.
- Rastreamento de abertura e clique.
- Campanhas de volume incompatível com contas SMTP pessoais.
