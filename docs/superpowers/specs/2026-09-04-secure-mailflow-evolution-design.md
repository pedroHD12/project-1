# MailFlow Local — evolução segura do produto

Data: 2026-09-04  
Estado: incrementos A/B e núcleo C/D implementados em setembro de 2026. C/D inclui envio revisado, confirmação, histórico, agendamento único e repetição diária/semanal finita. Importação, relatórios avançados, anexos, sequências condicionais, recuperação de senha e hospedagem permanecem fora desta entrega; esta especificação também registra a direção futura, não uma lista de recursos já disponíveis.

## 1. Objetivo

Evoluir o MailFlow Local de uma aplicação pessoal com um único usuário em memória para um produto web local com conta proprietária persistente, dados privados, navegação intuitiva, configuração simplificada do e-mail e fluxo seguro de envio.

A arquitetura deve permitir uma futura edição por assinatura sem implementar agora cobrança, planos comerciais, equipes ou publicação na internet.

## 2. Princípios

- Segurança e isolamento são requisitos funcionais, não melhorias posteriores.
- A primeira versão continua local e vinculada a `127.0.0.1`.
- O primeiro cadastro cria a conta proprietária; novos cadastros ficam fechados por padrão.
- Toda operação de domínio é limitada ao espaço privado autenticado.
- A interface usa linguagem comum; termos SMTP permanecem apenas no código e nas opções avançadas.
- Nenhuma operação de envio real acontece sem revisão e confirmação explícita.
- Mudanças de banco são incrementais, verificáveis e reversíveis por restauração de backup.
- Cada incremento deve terminar funcional, testado e sem depender de páginas fictícias.

## 3. Escopo e decomposição

O trabalho será entregue em quatro incrementos independentes.

### Incremento A — fundação segura

- Usuário persistente e espaço privado.
- Primeiro cadastro proprietário e login em português.
- Migração sem perda dos dados existentes.
- Isolamento de contatos, modelos, contas de envio e demais dados de domínio.
- Controles de sessão, CSRF, tentativas de login, logs e segredos.
- Testes unitários, de integração, autorização e migração.

### Incremento B — interface e configuração do e-mail

- Layout Thymeleaf compartilhado com barra lateral responsiva.
- Navegação principal e seção recolhível “Mais recursos”.
- Onboarding do primeiro uso.
- “SMTP” passa a ser apresentado como “Meu e-mail”.
- Presets seguros para Gmail/Google Workspace e configuração manual de outro serviço. Outlook/Hotmail foi adiado: precisa de OAuth2, não apenas de um preset SMTP com senha.
- Configurações técnicas dentro de uma seção avançada.

### Incremento C — novo envio

- Seleção de destinatários e modelo.
- Edição de assunto e conteúdo.
- Revisão, envio de teste para o próprio usuário e confirmação final.
- Idempotência contra cliques duplos e repetição de requisições.
- Registro de resultado e erros públicos sem detalhes confidenciais.

### Incremento D — automações e recursos secundários

- Agendamentos e automações após o envio imediato estar estável.
- Histórico, importação, relatórios, backup, limites e ajuda somente quando cada página tiver um fluxo real.
- Cobrança e assinaturas permanecem fora do escopo.

## 4. Arquitetura escolhida

O monólito Spring Boot com Spring MVC, Thymeleaf, JPA, Flyway e PostgreSQL será mantido. Não serão introduzidos SPA, microsserviços ou mensageria externa nesta fase.

### 4.1 Identidade e espaço privado

`app_users` armazena a identidade de login:

- `id` UUID;
- `workspace_id` UUID obrigatório e único nesta fase;
- `name`;
- `email` para apresentação;
- `normalized_email` único para autenticação;
- `password_hash`;
- `status`;
- dados de tentativas e bloqueio temporário;
- datas de criação e atualização.

`workspaces` representa a fronteira de dados:

- `id` UUID;
- `name`;
- datas de criação e atualização.

Cada usuário possui exatamente um workspace nesta versão. Equipes, membros e papéis não serão antecipados. Uma futura edição poderá adicionar memberships sem mudar a propriedade dos dados existentes.

### 4.2 Contexto autenticado

Um principal autenticado contém o UUID do usuário e do workspace. Controllers não aceitam `workspace_id` vindo do navegador. Serviços recebem o contexto autenticado e repositórios consultam sempre por `id + workspace_id`.

Regras obrigatórias:

- listas, contagens e buscas são filtradas pelo workspace;
- edição, exclusão, diagnóstico e envio também são filtrados;
- um UUID de outro workspace responde como registro inexistente;
- o dashboard nunca usa contagens globais;
- unicidades de domínio são compostas pelo workspace;
- filhos e associações não podem conectar registros de workspaces diferentes.

## 5. Migração de dados

A migração ocorrerá em etapas no PostgreSQL:

1. Criar `workspaces` e `app_users`.
2. Criar um workspace inicial com UUID estável.
3. Adicionar `workspace_id` inicialmente preenchível às tabelas de domínio.
4. Associar todos os registros existentes ao workspace inicial.
5. Verificar registros nulos, quantidades e integridade das relações.
6. Aplicar `NOT NULL`, índices e restrições compostas.
7. Remover defaults temporários usados apenas no backfill.

O primeiro cadastro seleciona e bloqueia transacionalmente o workspace inicial, cria a conta proprietária e fecha o cadastro. Uma restrição única no banco resolve corridas entre dois cadastros simultâneos; o perdedor recebe mensagem genérica orientando a entrar no sistema.

Antes da migração será exigido um backup verificável do banco. A reversão será feita restaurando esse backup e voltando à versão anterior da aplicação; não haverá migration destrutiva automática de downgrade.

## 6. Cadastro, login e sessão

### 6.1 Primeiro cadastro

- `GET /register` apresenta o formulário somente quando não há proprietário.
- `POST /register` aceita nome, e-mail, senha e confirmação.
- Depois da criação do proprietário, ambas as rotas deixam de aceitar cadastro.
- Cadastro público futuro exige uma decisão e um incremento de segurança próprios.

### 6.2 Senhas

- Senhas nunca são registradas nem armazenadas de forma reversível.
- O hash usa BCrypt com custo 12.
- O formulário exige pelo menos 12 caracteres e aceita no máximo 72 bytes UTF-8, respeitando o limite do BCrypt sem truncamento. Rejeita uma lista local mínima de senhas comuns; não há consulta externa durante o cadastro.
- A confirmação é validada no servidor.
- Recuperação de senha por e-mail não faz parte deste incremento.

### 6.3 Proteções

- Mensagem genérica para usuário ou senha incorretos.
- Depois de cinco falhas consecutivas, a conta fica bloqueada por cinco minutos; um login bem-sucedido zera o contador.
- Renovação do identificador de sessão após login.
- CSRF em todas as mutações.
- Cookies `HttpOnly` e `SameSite=Strict`; `Secure` é obrigatório em perfil hospedado com HTTPS.
- Cabeçalhos contra framing e sniffing; CSP compatível com os recursos locais.
- Logout por POST e invalidação da sessão.
- A senha administrativa temporária atual e seu log serão removidos.

## 7. Segredos e conta de envio

No modo local Windows, credenciais SMTP continuam protegidas pela abstração `SecretProtector` com DPAPI. O texto original nunca retorna para formulários, logs ou mensagens de erro.

A edição hospedada não poderá usar DPAPI como solução principal. Publicação na internet fica bloqueada até existir criptografia autenticada com chave externa ao banco, versionamento, rotação, HTTPS e política de acesso aos backups.

Presets de provedor serão definidos no backend:

- Gmail;
- Outlook/Hotmail: integração OAuth2 planejada, ainda indisponível;
- Outro e-mail, em modo avançado.

Para presets conhecidos, host, porta e TLS são derivados pelo servidor, ignorando valores adulterados pelo navegador. O sistema explica em linguagem comum que o provedor ainda pode exigir senha de aplicativo ou OAuth.

Antes de hospedagem pública, o modo manual também deverá bloquear loopback, link-local, redes privadas, portas não permitidas e DNS rebinding. Diagnóstico e envio terão limites de frequência e mensagens públicas sem detalhes de topologia.

## 8. Experiência e navegação

Um fragmento Thymeleaf compartilhado será usado em todas as páginas autenticadas.

Navegação principal:

- Painel;
- Novo envio;
- Contatos;
- Modelos de mensagem;
- Automações;
- Agendamentos.

“Mais recursos”, fechado por padrão:

- Histórico;
- Importação e exportação;
- Relatórios;
- Meu e-mail;
- Backup;
- Limites;
- Configurações;
- Ajuda.

Itens ainda inexistentes ficam ocultos ou marcados como “Em breve” sem link ativo. Perfil e sair ficam no rodapé da barra lateral.

No celular, a sidebar vira uma gaveta com botão visível, `aria-expanded`, foco controlado, fechamento por Escape e restauração de foco. A página ativa usa `aria-current`.

O visual escuro existente será preservado, com melhoria de contraste, foco visível, hierarquia, espaçamento e estados de erro. Serão evitados cartões aninhados, sombras decorativas largas e arredondamento excessivo.

## 9. Fluxo de novo envio

O fluxo será dividido em cinco passos claros:

1. Selecionar destinatários.
2. Escolher um modelo ou escrever a mensagem.
3. Definir assunto e revisar conteúdo.
4. Enviar um teste opcional para o próprio endereço autenticado.
5. Confirmar o envio real, mostrando conta de envio, destinatários e assunto.

Cada confirmação recebe uma chave de idempotência armazenada no servidor. Repetir a mesma requisição não cria outro envio. Falhas por destinatário são registradas sem expor credenciais ou o corpo da mensagem em logs.

HTML de modelos será sanitizado por allowlist antes de qualquer pré-visualização. A prévia será isolada; conteúdo de usuário não será renderizado diretamente no shell da aplicação.

## 10. Tratamento de erros e auditoria

- Erros públicos usam linguagem simples e um código de correlação.
- Detalhes técnicos ficam apenas em log protegido e sempre redigido.
- Nunca registrar senha, token, cookie, cabeçalho de autorização ou corpo completo de e-mail.
- Eventos de login, bloqueio, alteração de credencial, diagnóstico e envio são auditados com workspace, usuário, data e resultado.
- A auditoria não armazena o segredo SMTP nem conteúdo integral da mensagem.
- Violações de unicidade por concorrência retornam erro de formulário, não resposta 500.

## 11. Estratégia de testes

O desenvolvimento segue teste primeiro para comportamentos de segurança e domínio.

### 11.1 Autenticação

- primeiro cadastro cria proprietário e fecha o registro;
- dois cadastros simultâneos geram somente uma conta;
- e-mail é comparado de forma normalizada;
- senha original não aparece no banco ou logs;
- login incorreto usa mensagem genérica;
- cinco falhas consecutivas bloqueiam a conta por cinco minutos e um login posterior bem-sucedido zera o contador;
- logout invalida a sessão;
- mutações sem CSRF são recusadas.

### 11.2 Isolamento

Com dois workspaces de teste, o usuário A não pode listar, contar, abrir, editar, excluir, diagnosticar ou enviar usando dados de B. Os testes cobrem GET, POST, IDs válidos de outro usuário e associações cruzadas.

### 11.3 Banco e migração

- migrations executadas em PostgreSQL real temporário via embedded PostgreSQL, sem depender do banco pessoal; a validação desta entrega usa PostgreSQL 14.22, não o PostgreSQL 17 do Compose;
- dados legados preservam IDs e relações;
- nenhum registro fica sem workspace;
- unicidades funcionam dentro do workspace e permitem repetição em outro;
- falhas de migration interrompem a inicialização;
- consultas concorrentes retornam resultado controlado.

### 11.4 E-mail e entradas hostis

- adulteração de host, porta e TLS nos presets é ignorada;
- criação sem segredo não pode ser forjada por campo oculto;
- mensagens SMTP não vazam detalhes internos;
- sanitização remove scripts, handlers e URLs perigosas;
- confirmação duplicada não envia duas vezes;
- limites de diagnóstico e envio são exercitados.

### 11.5 Interface

- login, cadastro, navegação, conta de envio e novo envio são validados no navegador;
- larguras de 320, 620, 720, 900 e desktop;
- navegação por teclado, foco visível, Escape e redução de movimento;
- contraste mínimo WCAG AA para texto e placeholders;
- estados vazios, erros, carregamento e sucesso.

### 11.6 Verificações finais

- testes focados por módulo;
- suíte completa;
- build limpo;
- revisão do diff;
- varredura de dependências quando a ferramenta estiver disponível;
- varredura profunda do repositório e revisão de segurança das mudanças;
- nova rodada independente dos cinco jurados.

Nenhum teste fará envio real sem configuração e autorização explícitas. Gateways externos serão simulados por padrão.

## 12. Critérios de aceite

- O primeiro proprietário pode cadastrar-se, entrar e sair usando páginas em português.
- Os dados anteriores permanecem disponíveis para essa conta.
- Não existe caminho web conhecido para acessar dados de outro workspace.
- Todos os serviços e contadores de domínio usam o workspace autenticado.
- Segredos não aparecem em banco em texto simples, HTML, logs ou respostas.
- A navegação funciona em desktop, celular e teclado.
- A configuração comum de Gmail não exige digitar host, porta ou TLS. Outlook depende da futura integração OAuth2.
- O envio real exige revisão e confirmação e não duplica por repetição da requisição.
- Testes de PostgreSQL, segurança, interface e regressão passam antes de declarar conclusão.
- A aplicação continua limitada a localhost enquanto os requisitos de hospedagem não forem satisfeitos.

## 13. Fora do escopo

- Pagamentos, assinaturas e planos.
- Cadastro público hospedado.
- Equipes, convites e papéis múltiplos.
- OAuth de Google ou Microsoft neste ciclo.
- Rastreamento de abertura ou cliques.
- Campanhas em massa e marketing não solicitado.
- Aplicativo móvel nativo.
- Migração para SPA ou microsserviços.

## 14. Ordem de implementação

1. Baseline recuperável e backup verificável.
2. Testes e migrations de workspace.
3. Usuário persistente, primeiro cadastro e login.
4. Isolamento completo e testes adversariais.
5. Layout compartilhado e onboarding.
6. Assistente “Meu e-mail”.
7. Fluxo de novo envio.
8. Agendamento e automações.
9. Auditoria final, correções e cinco jurados.

Cada incremento terá um plano próprio. O primeiro plano cobrirá somente os itens 1 a 4, porque eles formam a fronteira mínima de segurança para qualquer evolução posterior.
