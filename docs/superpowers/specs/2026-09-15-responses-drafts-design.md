# Respostas, rascunhos e interface

## Escopo autorizado

Implementar leitura opcional de respostas do Gmail, salvamento manual persistente de rascunhos e melhoria da interface existente. Java 25, Spring Boot, Thymeleaf e PostgreSQL continuam sendo a base. A instalação continua local, vinculada a 127.0.0.1. Testes usam dados sintéticos; conectar a conta pessoal é uma ação explícita no produto.

## Rascunhos

Um rascunho salvo é independente da fila de envio. Pode estar incompleto, ser editado, revisado e reutilizado. Revisar ou enviar gera uma mensagem no fluxo já existente, sem apagar o original. Exclusão exige ação manual autenticada com CSRF. Conteúdo HTML só aparece escapado no editor; a revisão aplica o sanitizador existente. Cada atualização exige a versão vista pelo usuário, impedindo sobrescrita silenciosa entre abas. A listagem tem paginação de 50 itens, sem expiração; a cota é de 1.000 rascunhos por espaço privado.

## Leitura de respostas

SMTP envia; IMAP lê. A primeira integração aceita as contas já configuradas para smtp.gmail.com, com identidade consistente e senha de aplicativo protegida por DPAPI. O destino de leitura é fixo: imap.gmail.com:993 com TLS, identidade do servidor verificada e tempo limite. A autorização para sincronizar é separada, inicialmente desligada e vinculada à identidade da conta.

Consultar INBOX em modo somente leitura, sem marcar como lido, mover ou apagar. Sincronizar automaticamente a cada dois minutos quando habilitado e permitir atualização manual. Usar cursor UID/UIDVALIDITY persistido e reserva com token para evitar sincronizações concorrentes. Primeira leitura considera até os últimos 200 UIDs; leituras seguintes avançam em lotes limitados. Não importar toda a caixa: persistir apenas respostas cujos In-Reply-To/References apontem para um identificador de envio do MailFlow e cujo remetente corresponda ao destinatário desse envio.

O trabalhador passa Message-ID determinístico por tarefa, no formato <UUID@mailflow.local>. A correlação não se aplica retroativamente a e-mails anteriores a esta versão e não é prova de autenticidade do remetente. Ler texto somente das respostas relacionadas; limitar tamanho, profundidade e quantidade de partes MIME. Exibir como texto escapado, sem carregar imagens, executar HTML ou baixar anexos recebidos. Falhas externas viram códigos e textos genéricos. Senhas, corpos e cabeçalhos não entram em logs.

Desligar a sincronização não apaga respostas já salvas. Pausar envios e receber respostas são operações independentes. OAuth, Outlook, resposta automática, download de anexos recebidos e upload de anexos permanecem fora deste incremento.

## Interface — direction contract

THESIS: um espaço de trabalho que deixa escrever, guardar, enviar e acompanhar respostas visíveis na navegação.

OWN-WORLD: preservar o tema escuro, neutralizar brilho e gradientes, usar violeta apenas para seleção/ação, controles consistentes e fonte de sistema.

STORY: compor → salvar ou revisar → confirmar → acompanhar; a leitura da caixa explica seu estado e autorização.

FIRST VIEWPORT: título e ação principal no topo; estados e listas logo abaixo; botões Salvar rascunho e Revisar mensagem juntos no editor; abas Rascunhos e Respostas no menu principal.

FORM: extensão direta da superfície existente em modo Operate, sem substituição da identidade visual. O motor Impeccable está indisponível nesta máquina; aplicar suas referências ao código e validar a renderização quando disponível.

FINISH: interface revisada, documentação de uso atualizada e verificações registradas, sem afirmar validação da conta Gmail pessoal.

## Critérios de aceitação

- Salvar uma mensagem incompleta não cria tarefas nem envia e-mails.
- Rascunhos permanecem após revisão, envio e reinício; só exclusão manual os remove.
- Tentativas de acessar dados de outro espaço e alterações concorrentes são recusadas.
- Sincronização não conecta antes da autorização e não reusa a autorização com outra identidade.
- Message-ID chega ao SMTP sintético e identifica a resposta correspondente.
- Respostas repetidas não duplicam registros; conteúdo hostil é apresentado como texto.
- Mudança UIDVALIDITY, conexão recusada e reserva concorrente têm comportamento testado.
- Compilação e testes de regressão passam; restauração da conta pessoal não é usada como fixture.
