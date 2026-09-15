# MailFlow Local

Aplicação web pessoal em Java 25, Spring Boot, Thymeleaf e PostgreSQL, acessível somente neste computador Windows.

**Estado: núcleo de uso pessoal implementado.** Cadastro, login, contatos, modelos, conta de envio, revisão, envio imediato, agendamento, repetição diária/semanal e histórico estão disponíveis. A aplicação é local; não é uma edição SaaS pronta para publicação.

## O que funciona

- Primeiro cadastro persistente: uma única conta proprietária assume os dados locais existentes. O cadastro fecha depois disso.
- Login, saída, sessão de 30 minutos de inatividade e proteção contra tentativas repetidas.
- Contatos: cadastrar, editar, pesquisar, bloquear e excluir.
- Modelos de mensagem: criar, editar, arquivar e restaurar; carregar no novo envio e personalizar com `{{nome}}` e `{{email}}`.
- Barra lateral compartilhada, menu para telas pequenas e seção recolhível **Mais recursos**.
- **Meu e-mail:** Gmail com preenchimento do servidor, porta e proteção pelo backend; Google Workspace por seleção explícita; outro serviço com configuração manual protegida.
- Credenciais de envio protegidas pelo DPAPI do usuário atual do Windows.
- Testar conexão sem mensagem; enviar um teste individual somente após confirmação explícita.
- **Novo envio:** escolher contatos, escrever ou carregar um modelo, revisar a cópia por destinatário e confirmar. Um rascunho não dispara mensagens.
- Envio imediato, agendamento único e automações finitas diárias ou semanais; pausar, retomar e cancelar os próximos disparos.
- Histórico por destinatário, com tentativas, horários, resultados e liberação explícita de envios atrasados.
- HTML sanitizado, sem imagens, links, scripts ou rastreadores, com prévia isolada. Cada destinatário recebe uma mensagem separada.

Informar apenas um endereço não autoriza enviar por ele. Gmail requer uma senha de aplicativo permitida pela conta Google; não use sua senha normal. [Instruções oficiais do Google](https://support.google.com/accounts/answer/185833?hl=pt-BR).

**Outlook/Hotmail/Microsoft 365:** integração adiada. A Microsoft exige autorização moderna/OAuth2; um preset de servidor com senha não entrega esse login. [Requisitos oficiais da Microsoft](https://support.microsoft.com/en-us/outlook/pop-imap-and-smtp-settings-for-outlook-com). OAuth Google também não está implementado.

## Antes de atualizar uma instalação existente

Pare a aplicação e faça um backup verificável do PostgreSQL antes de iniciar esta versão. As migrações são automáticas no início: V3 preserva os registros/UUIDs e os associa ao espaço privado inicial; V4 acrescenta o novo fluxo de envio. Mensagens e tarefas antigas não são ativadas automaticamente.

Com o banco do Compose, por exemplo:

```powershell
New-Item -ItemType Directory -Force backups
docker exec mailflow-postgres pg_dump -U mailflow -d mailflow -Fc -f /tmp/mailflow-before-foundation.dump
docker cp mailflow-postgres:/tmp/mailflow-before-foundation.dump ./backups/mailflow-before-foundation.dump
Get-FileHash ./backups/mailflow-before-foundation.dump -Algorithm SHA256
```

Não sobrescreva um backup anterior: use nomes datados. Confirme o código de saída de cada comando e teste a restauração em outro banco. Para listar o conteúdo, use `pg_restore --list arquivo.dump` com ferramentas compatíveis com seu PostgreSQL.

Rollback exige restaurar o backup em um banco separado e executar a versão anterior apontando para ele; não há downgrade destrutivo automático. A restauração preserva a senha de login, não serve para removê-la. Credenciais DPAPI dependem também do ambiente/usuário Windows original; o dump sozinho não garante a recuperação delas em outro computador.

## Como iniciar

Pré-requisitos: Windows, Java 25, PostgreSQL local ou Docker Compose. Maven é necessário apenas para compilar.

### 1. Banco

Se usar Docker, copie `.env.example` para `.env`, substitua `DB_PASSWORD` por uma senha exclusiva e execute na pasta do projeto:

```powershell
docker compose up -d postgres
```

O PostgreSQL é exposto somente em `127.0.0.1:5432`. Se já tem PostgreSQL, use um banco exclusivo do MailFlow e um usuário com permissão para executar suas migrações. O Compose não troca a senha de um volume já criado: preserve a senha atual desse banco.

### 2. Compilar, se necessário

```powershell
mvn package
```

Neste computador, o Maven local usado no desenvolvimento também está disponível em:

```powershell
./.local-tools/apache-maven-3.9.11/bin/mvn.cmd package
```

O executável fica em `target/mailflow-local-0.1.0-SNAPSHOT.jar`. Não use um JAR antigo para testar o código novo.

### 3. Executar

No PowerShell, na pasta do projeto, informe a mesma senha configurada no banco; ela não aparecerá na tela:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/mailflow"
$env:DB_USER = "mailflow"
$mailflowDbSecret = Read-Host "Senha do banco MailFlow" -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new("", $mailflowDbSecret).Password
java -jar target/mailflow-local-0.1.0-SNAPSHOT.jar
```

A aplicação exige `DB_PASSWORD`; não existe senha padrão embutida. O Spring Boot **não carrega** o arquivo `.env` automaticamente. Mantenha o processo Java aberto enquanto usa o site; Ctrl+C o encerra.

### 4. Acessar

Abra [http://localhost:8080](http://localhost:8080) e clique em **Criar minha conta** no primeiro uso. Depois entre com esse e-mail e a senha do MailFlow.

Não existem mais `APP_USERNAME`, `APP_PASSWORD`, usuário admin fixo ou senha de acesso gerada no terminal. Conclua o primeiro cadastro pessoalmente antes de deixar a instalação sem supervisão. Quem conclui esse primeiro cadastro assume os dados locais já existentes.

**Guarde sua senha:** a recuperação de conta ainda não está implementada. Não apague o banco para recuperar acesso. A senha do MailFlow é diferente da senha de aplicativo do Gmail.

Em **Início**, siga os links para conectar seu e-mail, adicionar contatos e criar modelos. **Mais recursos → Meu e-mail** permite editar a conta e testar a conexão.

## Primeiro envio e automações

1. Cadastre um contato em **Contatos** e configure **Meu e-mail**. Teste a conexão com seu provedor.
2. Abra **Novo envio**, escolha a conta e os contatos. Escreva a mensagem ou carregue um modelo.
3. Escolha enviar agora, uma vez no futuro, diariamente ou semanalmente. Para agendamentos, confira data, hora e fuso; as repetições são limitadas e mantêm o horário local.
4. Clique em **Revisar mensagem**. Confira cada destinatário e a prévia. O teste opcional vai somente ao e-mail do seu login, uma vez por rascunho, sem confirmar o envio original.
5. Confirme o envio ou agendamento. Acompanhe em **Histórico**, **Agendamentos** ou **Automações**; clique em **Atualizar resultados** para consultar o estado mais recente.

O computador, PostgreSQL e processo Java precisam permanecer ligados. A fila é persistida e consultada a cada cinco segundos. Ao voltar, disparos atrasados mais de 15 minutos exigem **Enviar esta agora**; não existe recuperação automática em massa. Pausar ou cancelar não interrompe uma transmissão já iniciada.

**Aceito pelo provedor** não significa entregue na caixa de entrada. Se houver queda durante a transmissão, o resultado fica **incerto**, sem repetição automática: confira os registros do provedor antes de preparar outra mensagem. Falhas de conexão anteriores à transmissão permitem até três tentativas com espera. Uma tentativa em andamento abandonada por mais de cinco minutos também fica incerta.

Limites locais: 20 destinatários por mensagem, até 30 ocorrências, 12 tentativas por minuto e 200 em 24 horas (compartilhadas entre contas na fila). Os limites do provedor podem ser menores. As listas mostram até 100 registros recentes; a busca de contatos no novo envio permite refinar a seleção. Não há sequências condicionais, anexos, importação/exportação ou relatórios avançados nesta entrega.

Se aparecer conexão recusada, confira se o processo Java terminou a inicialização e se a porta configurada é 8080. Não desative firewall nem exponha a aplicação para resolver isso.

## Segurança e limites

- CSRF nos formulários, CSP sem scripts inline e bloqueio de Host/origem de rede não local.
- BCrypt custo 12, senha mínima de 12 caracteres, máximo de 72 bytes UTF-8; tentativas inválidas limitadas e bloqueio temporário por conta.
- Dados acessados pelo workspace do principal autenticado, nunca por um workspace enviado no formulário.
- Migração com FKs compostas para impedir relações entre workspaces.
- SMTP exige TLS e identidade do servidor; erros externos não são apresentados em detalhes.
- Credencial salva não é reutilizada ao mudar destino/identidade sem informá-la novamente; remover autenticação apaga a credencial cifrada inutilizada.
- Configuração salva/habilitada **não significa conexão verificada**. Use Testar conexão.
- Acesso ao Windows ou ao banco continua sendo um limite de confiança. Contatos e mensagens no banco não são cifrados pela aplicação.
- Mudanças em duas abas ainda podem sobrescrever edições; controle de versão de formulários fica para uma próxima etapa.
- Destinos SMTP manuais podem ser servidores internos do próprio usuário. Uma edição pública precisará de política de destinos/egresso contra SSRF.
- Confirmações repetidas não criam novos disparos; reservas da fila são protegidas contra processos concorrentes. SMTP não permite prometer entrega exatamente uma vez em todos os cenários de falha.
- Esta versão não deve ser publicada por túnel, roteador ou hospedagem. HTTPS, recuperação de conta, OAuth, autorização multiusuário, gestão de segredos e novos testes serão necessários para SaaS.

Nenhum conjunto de testes garante ausência de vulnerabilidades. Não foram enviados e-mails reais nem feita migração no banco pessoal durante a validação.

## Verificação

```powershell
mvn test
mvn package
```

Os testes usam H2 para integração rápida e PostgreSQL temporário real para migrações, relações, isolamento e conflitos concorrentes. Os testes SMTP usam servidores locais sintéticos; não contatam Gmail ou destinatários reais.

Evidências anteriores: [fundação segura](docs/SECURE-FOUNDATION-REVIEW.md). O [plano de envios](docs/superpowers/plans/2026-09-10-local-delivery.md) registra o escopo e as verificações desta evolução.

## Documentação

- [Plano executado](docs/superpowers/plans/2026-09-08-secure-foundation.md)
- [Plano de envios e automações](docs/superpowers/plans/2026-09-10-local-delivery.md)
- [Desenho e limites do produto](docs/superpowers/specs/2026-09-04-secure-mailflow-evolution-design.md)
- [Requisitos](docs/REQUISITOS.md)
