# MailFlow Local

Aplicação web pessoal em Java 25, Spring Boot, Thymeleaf e PostgreSQL, acessível somente neste computador Windows.

**Estado: núcleo pessoal implementado.** Cadastro, login, contatos, modelos, conta de envio, rascunhos salvos, revisão, envio imediato, agendamento, repetição diária/semanal, histórico e leitura opcional de respostas do Gmail estão disponíveis. Há também um perfil hospedado, privado e de proprietário único; ele ainda não é uma edição SaaS multiusuário.

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
- **Rascunhos:** salvar mesmo com campos incompletos, editar e reutilizar. Revisar ou enviar uma cópia não apaga o original; exclusão somente pelo usuário, sem prazo automático. Edições antigas em outra aba são recusadas.
- **Respostas:** conectar a leitura do Gmail separadamente do envio, atualizar manualmente ou a cada dois minutos e consultar respostas relacionadas aos envios desta versão. Não marca como lido, move ou apaga e-mails no Gmail.
- Envio imediato, agendamento único e automações finitas diárias ou semanais; pausar, retomar e cancelar os próximos disparos.
- Histórico por destinatário, com tentativas, horários, resultados e liberação explícita de envios atrasados.
- HTML sanitizado, sem imagens, links, scripts ou rastreadores, com prévia isolada. Cada destinatário recebe uma mensagem separada.

Informar apenas um endereço não autoriza enviar por ele. Gmail requer uma senha de aplicativo permitida pela conta Google; não use sua senha normal. [Instruções oficiais do Google](https://support.google.com/accounts/answer/185833?hl=pt-BR).

**Outlook/Hotmail/Microsoft 365:** integração adiada. A Microsoft exige autorização moderna/OAuth2; um preset de servidor com senha não entrega esse login. [Requisitos oficiais da Microsoft](https://support.microsoft.com/en-us/outlook/pop-imap-and-smtp-settings-for-outlook-com). OAuth Google também não está implementado.

## Antes de atualizar uma instalação existente

Pare a aplicação e faça um backup verificável do PostgreSQL antes de iniciar esta versão. As migrações são automáticas no início: V3 preserva os registros/UUIDs e os associa ao espaço privado inicial; V4 acrescenta o novo fluxo de envio; V5 cria rascunhos salvos, conexões de leitura e respostas recebidas. Mensagens e tarefas antigas não são ativadas automaticamente. As novas conexões de leitura começam desligadas.

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
& './mailflow-local/.local-tools/apache-maven-3.9.11/bin/mvn.cmd' package
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

Se seu PostgreSQL foi instalado na porta **8080**, use **8081 para o site**. São serviços diferentes; não podem ocupar a mesma porta. Exemplo desta instalação:

```powershell
Set-Location 'C:\Users\pedro\Documents\ChatGPT\projetos simples'
$env:DB_URL = 'jdbc:postgresql://localhost:8080/mailflow'
$env:DB_USER = 'postgres'
$env:APP_PORT = '8081'
$mailflowDbSecret = Read-Host 'Senha do PostgreSQL' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $mailflowDbSecret).Password
java -jar .\target\mailflow-local-0.1.0-SNAPSHOT.jar
```

A senha é digitada apenas quando aparecer `Senha do PostgreSQL:`; não substitua nada na linha `NetworkCredential`. Para uma instalação nova, prefira um usuário exclusivo do aplicativo em vez de `postgres`.

### 4. Acessar

Abra [http://localhost:8080](http://localhost:8080) e clique em **Criar minha conta** no primeiro uso. Depois entre com esse e-mail e a senha do MailFlow.

Se definiu `APP_PORT=8081`, abra [http://localhost:8081](http://localhost:8081). Ao atualizar, use o JAR da pasta `target` na **raiz** do projeto, não o antigo em `mailflow-local/target`. Espere o terminal indicar que a aplicação iniciou. Não copie `PS C:\...>`: isso é o indicador do PowerShell, não um comando.

Não existem mais `APP_USERNAME`, `APP_PASSWORD`, usuário admin fixo ou senha de acesso gerada no terminal. Conclua o primeiro cadastro pessoalmente antes de deixar a instalação sem supervisão. Quem conclui esse primeiro cadastro assume os dados locais já existentes.

**Guarde sua senha:** a recuperação de conta ainda não está implementada. Não apague o banco para recuperar acesso. A senha do MailFlow é diferente da senha de aplicativo do Gmail.

Em **Início**, siga os links para conectar seu e-mail, adicionar contatos e criar modelos. **Mais recursos → Meu e-mail** permite editar a conta e testar a conexão.

## Primeiro envio e automações

1. Cadastre um contato em **Contatos** e configure **Meu e-mail**. Teste a conexão com seu provedor.
2. Abra **Novo envio**, escolha a conta e os contatos. Escreva a mensagem ou carregue um modelo.
3. Escolha enviar agora, uma vez no futuro, diariamente ou semanalmente. Para agendamentos, confira data, hora e fuso; as repetições são limitadas e mantêm o horário local.
4. Clique em **Revisar mensagem**. Confira cada destinatário e a prévia. O teste opcional vai somente ao e-mail do seu login, uma vez por rascunho, sem confirmar o envio original.
5. Confirme o envio ou agendamento. Acompanhe em **Histórico**, **Agendamentos** ou **Automações**; clique em **Atualizar resultados** para consultar o estado mais recente.

Na instalação local, o computador, PostgreSQL e processo Java precisam permanecer ligados. A fila é persistida e consultada a cada cinco segundos. Ao voltar, uma tarefa pendente atrasada é enviada uma vez e fica identificada como **Enviado após o horário programado**; resultados incertos nunca são reenviados automaticamente. Pausar ou cancelar não interrompe uma transmissão já iniciada.

**Aceito pelo provedor** não significa entregue na caixa de entrada. Se houver queda durante a transmissão, o resultado fica **incerto**, sem repetição automática: confira os registros do provedor antes de preparar outra mensagem. Falhas de conexão anteriores à transmissão permitem até três tentativas com espera. Uma tentativa em andamento abandonada por mais de cinco minutos também fica incerta.

Limites locais: 20 destinatários por mensagem, até 30 ocorrências, 12 tentativas por minuto e 200 em 24 horas (compartilhadas entre contas na fila). Os limites do provedor podem ser menores. As listas antigas mostram até 100 registros recentes; rascunhos salvos e respostas usam páginas de 50 itens. Não há sequências condicionais, anexos, importação/exportação ou relatórios avançados nesta entrega.

Se aparecer conexão recusada, confira se o processo Java terminou a inicialização e se acessou a porta de `APP_PORT` (8080 por padrão). `Unable to access jarfile` significa pasta ou nome do JAR incorretos. Não desative firewall nem exponha a aplicação para resolver isso.

## Guardar uma mensagem para depois

1. Em **Novo envio**, escreva o que já sabe. Conta, destinatários e assunto podem ficar incompletos.
2. Clique em **Salvar rascunho**. Nada é enviado ou agendado.
3. Abra **Rascunhos → Editar** para completar ou alterar a mensagem.
4. Clique em **Revisar mensagem** quando estiver pronta. A confirmação envia uma cópia; seu rascunho continua guardado e pode ser reutilizado.
5. Para removê-lo, use **Excluir** na aba Rascunhos e confirme. Não existe exclusão por tempo, envio ou desligamento do computador. O limite é de 1.000 rascunhos por espaço privado.

Se outra aba salvou uma versão mais nova, reabra a versão salva antes de editar novamente. Rascunhos são diferentes dos modelos: o rascunho guarda um envio em preparação; o modelo é um texto reutilizável.

## Acompanhar respostas do Gmail

1. Configure sua conta em **Mais recursos → Meu e-mail**, usando a senha de aplicativo do Gmail, quando permitida pela conta. Não use sua senha normal.
2. Abra **Respostas** e leia o aviso de autorização. Clique em **Conectar respostas** para habilitar a consulta dessa conta.
3. Envie uma mensagem pela versão atual do MailFlow e aguarde a resposta do destinatário.
4. Clique em **Atualizar** ou aguarde a consulta automática. Ela acontece enquanto o computador, banco, aplicativo e internet estiverem disponíveis.
5. Clique em **Ler resposta** para ler o texto ou **Abrir envio original**. **Desconectar** interrompe novas consultas e preserva as cópias locais. A seção **Como a leitura funciona** explica autorização e limites.

O leitor usa somente `imap.gmail.com:993`, com TLS e verificação do certificado, e abre INBOX em modo somente leitura. A senha de aplicativo é compartilhada com a conta de envio protegida pelo Windows; autorizar a leitura é uma escolha separada. Google recomenda OAuth; contas que não permitem senhas de aplicativo precisam dessa integração futura. [Documentação oficial de IMAP do Gmail](https://developers.google.com/workspace/gmail/imap/imap-smtp).

Limites e comportamento:

- Não é uma cópia completa do Gmail. Na primeira consulta, examina um intervalo de até 200 UIDs recentes; consultas seguintes avançam em lotes limitados. Cabeçalhos de mensagens não relacionadas são consultados, mas seus corpos não são importados.
- A resposta precisa referenciar o identificador do envio e vir do endereço do destinatário original, na mesma conta. Essa relação **não comprova a autenticidade do remetente**. Não há resposta automática.
- Envios anteriores à atualização, mensagens sem esses cabeçalhos, encaminhamentos e mensagens fora da caixa de entrada podem não aparecer. Consulte o Gmail nesses casos.
- Exibe texto escapado, sem executar HTML, carregar imagens externas ou baixar anexos. Textos grandes ou além do limite do lote recebem um aviso para leitura no Gmail. Limites: 50.000 caracteres de texto, até 20 corpos por lote e 5.000 cópias locais ativas por espaço privado.
- **Excluir cópia local** limpa o conteúdo salvo no aplicativo, não o e-mail no Gmail. Uma chave de deduplicação permanece para impedir reimportação da mesma resposta; backups antigos ainda podem conter o conteúdo.
- Trocar a identidade da conta exige nova autorização. Erros de consulta não mostram detalhes externos nem avançam o cursor; uma atualização já em andamento pode precisar terminar antes da próxima.

Outlook, OAuth, download de anexos recebidos e upload de arquivos ficam fora desta entrega.

## Onde ficam Java, HTML, CSS, JavaScript e SQL

No GitHub, comece na raiz de `project-1`, abra **src → main** e escolha:

| O que procurar | Caminho dentro do repositório | Para que serve |
|---|---|---|
| Java | `src/main/java/br/com/mailflow/` | Regras do sistema, segurança, banco, envio e leitura de respostas |
| HTML | `src/main/resources/templates/` | Telas renderizadas pelo Thymeleaf: início, login, editor, rascunhos e respostas |
| CSS | `src/main/resources/static/css/app.css` | Cores, fontes, espaçamento e adaptação para celular |
| JavaScript | `src/main/resources/static/js/` | Menu, confirmação de ações e campos interativos |
| SQL | `src/main/resources/db/migration/` | Criação e evolução das tabelas do PostgreSQL |
| Configuração | `src/main/resources/application.yml` | Portas, sessão, banco e opções de execução |
| Testes | `src/test/java/br/com/mailflow/` | Verificações automatizadas sem usar sua conta real |
| Dependências | `pom.xml` | Bibliotecas, versão do Java e compilação pelo Maven |

**Exemplo para encontrar uma tela:** `src → main → resources → templates → delivery → form.html` é o editor de mensagens. `drafts/list.html` mostra os rascunhos e `inbox/list.html` mostra as respostas. Em Java, as pastas `delivery`, `draft` e `inbox` contêm as regras correspondentes.

No computador, abra a pasta do projeto em um editor, como VS Code ou IntelliJ, e navegue pelos mesmos caminhos. Um arquivo `.java` aparece como `MailFlowApplication.java`; as subpastas separam cada funcionalidade.

Não abra o HTML com dois cliques para executar o site: esses arquivos dependem dos dados e rotas do Java. Inicie o JAR e use o navegador. Depois de alterar código ou telas, compile novamente e reinicie o JAR para carregar as mudanças. Não altere migrações já aplicadas; crie uma nova migração SQL para mudanças futuras.

`target/` contém arquivos gerados, não o código-fonte, e não vai ao GitHub. `.env`, senhas, banco, backups e ferramentas locais também não devem ser enviados ao repositório. `compose.yaml` é uma opção para rodar o banco com Docker, não uma exigência quando PostgreSQL já está instalado.

## Segurança e limites

- CSRF nos formulários, CSP sem scripts inline e bloqueio de Host/origem de rede não local.
- BCrypt custo 12, senha mínima de 12 caracteres, máximo de 72 bytes UTF-8; tentativas inválidas limitadas e bloqueio temporário por conta.
- Dados acessados pelo workspace do principal autenticado, nunca por um workspace enviado no formulário.
- Migração com FKs compostas para impedir relações entre workspaces.
- SMTP e leitura Gmail exigem TLS e identidade do servidor; erros externos não são apresentados em detalhes. Leitura começa desligada e usa reserva com token para descartar resultados após desautorização.
- Credencial salva não é reutilizada ao mudar destino/identidade sem informá-la novamente; remover autenticação apaga a credencial cifrada inutilizada.
- Configuração salva/habilitada **não significa conexão verificada**. Use Testar conexão.
- Acesso ao Windows ou ao banco continua sendo um limite de confiança. Contatos, mensagens, rascunhos e respostas no banco não são cifrados pela aplicação. Proteja também os backups.
- Rascunhos salvos têm controle de versão. Outros formulários ainda podem sobrescrever edições feitas em duas abas.
- Destinos SMTP manuais podem ser servidores internos do próprio usuário. Uma edição pública precisará de política de destinos/egresso contra SSRF.
- Confirmações repetidas não criam novos disparos; reservas da fila são protegidas contra processos concorrentes. SMTP não permite prometer entrega exatamente uma vez em todos os cenários de falha.
- Esta versão não deve ser publicada por túnel, roteador ou hospedagem. HTTPS, recuperação de conta, OAuth, autorização multiusuário, gestão de segredos e novos testes serão necessários para SaaS.

Nenhum conjunto de testes garante ausência de vulnerabilidades. Não foram enviados e-mails reais nem feita migração no banco pessoal durante a validação.

## Verificação

```powershell
mvn test
mvn package
```

Os testes usam H2 para integração rápida e PostgreSQL temporário real para migrações, relações, isolamento e conflitos concorrentes. Os testes SMTP usam servidores locais sintéticos; os testes de caixa usam leitores simulados e mensagens MIME fictícias. Não contatam Gmail ou destinatários reais. A prévia `WorkspaceUiPreview`, exclusiva de testes na porta 8083, também substitui os dois transportes e usa outro banco temporário.

Verificação final de 18/09/2026: **99 testes, 0 falhas, 0 erros e 0 ignorados**. Consulte o [fechamento de respostas, rascunhos e interface](docs/RESPONSES-DRAFTS-REVIEW.md).

Evidências anteriores: [fundação segura](docs/SECURE-FOUNDATION-REVIEW.md). O [plano de envios](docs/superpowers/plans/2026-09-10-local-delivery.md) registra o escopo e as verificações desta evolução.

## Documentação

- [Plano executado](docs/superpowers/plans/2026-09-08-secure-foundation.md)
- [Plano de envios e automações](docs/superpowers/plans/2026-09-10-local-delivery.md)
- [Plano de respostas e rascunhos](docs/superpowers/plans/2026-09-15-responses-drafts.md)
- [Desenho das respostas, rascunhos e interface](docs/superpowers/specs/2026-09-15-responses-drafts-design.md)
- [Fechamento de respostas, rascunhos e interface](docs/RESPONSES-DRAFTS-REVIEW.md)
- [Implantação privada em Oracle VM + Supabase](docs/DEPLOY-ORACLE-SUPABASE.md)
- [Checklist de publicação privada](docs/HOSTED-RELEASE-CHECKLIST.md)
- [Desenho e limites do produto](docs/superpowers/specs/2026-09-04-secure-mailflow-evolution-design.md)
- [Requisitos](docs/REQUISITOS.md)
