# MailFlow hospedado: desenho da primeira versão online

## Objetivo

Transformar o MailFlow Local em um site pessoal hospedado. O site deve permanecer acessível por HTTPS e processar envios agendados mesmo que o computador do proprietário esteja desligado. A primeira versão online terá somente um proprietário autorizado. Assinaturas, pagamentos e cadastro público ficam fora de escopo.

## Decisões de produto

- O produto será um site; não haverá instalador Windows nesta etapa.
- O proprietário usa a interface atual de contatos, modelos, rascunhos, envios, respostas e configurações de e-mail.
- Não haverá autoatendimento, convite ou criação pública de contas. A base de isolamento por `workspace_id` continua, para permitir evolução futura para SaaS.
- Envios que deveriam ter ocorrido durante uma indisponibilidade serão enviados uma única vez quando o serviço voltar. A interface mostrará que foram enviados com atraso.
- A versão inicial será orientada a custo zero, sem prometer disponibilidade contratual. Um plano pago será necessário antes de atender clientes.

## Arquitetura

```text
Navegador do proprietário
        |
      HTTPS
        |
Proxy TLS no servidor Oracle Always Free
        |
Spring Boot / Thymeleaf (Docker, uma instância persistente)
        |                           |
        | TLS                       | SMTP/IMAP com TLS
        v                           v
Supabase PostgreSQL             Gmail/outro provedor
```

O repositório GitHub armazena somente código, configuração sem segredos e o fluxo de build/deploy. O Java permanece no servidor Oracle; GitHub Pages não é usado. O banco é Supabase PostgreSQL e só aceita conexões do backend usando TLS. O navegador nunca recebe a senha do banco nem uma credencial SMTP.

O proxy TLS recebe conexões públicas e encaminha somente para a aplicação local. A porta do Spring não será exposta diretamente. Firewall: liberar apenas HTTPS e acesso administrativo restrito. O endereço definitivo precisa ter certificado HTTPS válido antes de qualquer dado real.

## Perfis de execução

### `local`

O perfil atual continua suportado para desenvolvimento: PostgreSQL local, filtro de localhost, HTTP local e proteção DPAPI do Windows.

### `cloud`

O novo perfil é obrigatório na hospedagem. Ele:

- remove o bloqueio de localhost e aceita somente cabeçalhos encaminhados pelo proxy configurado;
- exige HTTPS para sessão, habilita HSTS e usa cookies `Secure`, `HttpOnly` e `SameSite=Strict`;
- liga cache de templates;
- usa a URL PostgreSQL com TLS e verificação de certificado;
- não inicializa se faltarem os segredos obrigatórios;
- não permite usar o protetor DPAPI.

## Proprietário único

Em cloud, `/register` deixa de existir. O primeiro acesso é um fluxo de configuração único:

1. O deploy define `OWNER_EMAIL` e `INITIAL_OWNER_SETUP_TOKEN` como segredos aleatórios.
2. `/setup` aceita o token somente enquanto não existir nenhum usuário.
3. O e-mail digitado precisa corresponder exatamente a `OWNER_EMAIL`; o proprietário escolhe nome e senha.
4. Após criar a conta, `/setup` e `/register` retornam 404, mesmo após reiniciar o servidor.

O token de configuração não é registrado em logs, URL, banco ou GitHub. O login existente mantém hash BCrypt, limite de tentativas e bloqueio temporário. Recuperação de senha, convites e múltiplos usuários serão recursos futuros, não atalhos inseguros nesta versão.

## Proteção de credenciais de e-mail

DPAPI é específico do Windows e não serve no servidor. Em cloud haverá um `SecretProtector` AES-256-GCM:

- a chave de 32 bytes vem somente do segredo `MAILFLOW_CREDENTIAL_KEY` da hospedagem;
- cada valor tem nonce aleatório, versão de chave e texto cifrado autenticado;
- a coluna existente de credencial armazena apenas a versão e o conteúdo cifrado;
- credenciais não entram em exceções, auditoria, métricas, respostas HTTP ou logs;
- a aplicação rejeita dados DPAPI em cloud e rejeita dados AES-GCM em local, evitando uso acidental entre ambientes;
- rotação usa uma segunda chave temporária: novos dados usam a chave ativa e os dados antigos são recifrados de forma controlada antes de remover a anterior.

O Gmail continuará usando senha de aplicativo, nunca a senha normal da conta. OAuth pode substituir senhas de aplicativo em uma etapa futura.

## Agendamento e entrega

O worker atual será convertido de polling em memória para uma fila persistente e recuperável.

- Um job pendente pode ser reivindicado somente por uma transação; PostgreSQL usa bloqueio e `SKIP LOCKED`.
- O claim recebe token e lease. Um worker morto deixa um job recuperável após o lease expirar.
- Quando o horário de um job já passou, ele é reivindicado e enviado com o estado final `SENT_LATE`, e não marcado como `MISSED`.
- Antes do SMTP, o worker confirma que a mensagem continua confirmada, o contato está ativo, o destinatário não foi bloqueado e a conta SMTP não mudou.
- O `Message-ID` é determinístico por job. O SMTP não oferece exatamente-uma-vez quando a conexão cai após a aceitação; nesse caso o estado será `UNKNOWN` e o sistema não reenviará automaticamente.
- Falhas temporárias têm no máximo três tentativas com atraso progressivo. Falhas permanentes e estados desconhecidos exigem revisão manual.
- Limites de taxa continuam persistidos no banco, portanto valem mesmo após reinício.
- O polling IMAP para respostas será isolado do worker de entrega. Falha na caixa de entrada não para envios.

Não haverá recorrência diária genérica nesta migração sem uma tela de regras e revisão específica. O requisito atendido aqui é que mensagens agendadas já existentes sobrevivem ao desligamento do computador e à indisponibilidade do servidor.

## Dados, migrações e backup

Flyway continua sendo a única forma de alterar o esquema. Uma migração adicionará os estados de recuperação e os metadados de criptografia; não alterará ou apagará dados existentes silenciosamente.

Antes de migrar a primeira instalação real, será criado um backup lógico do PostgreSQL. O deploy executa migrações antes de receber tráfego. Falha de migração cancela a publicação.

Backups regulares precisam ser configurados fora da aplicação e testados com restauração. O plano gratuito não substitui backup.

## Deploy e operação

1. GitHub Actions executa testes e gera uma imagem Docker sem segredos.
2. A imagem é publicada em registro privado ou no registro da conta do proprietário.
3. O servidor obtém a imagem e reinicia de modo controlado.
4. Health check, migração e smoke test precisam passar antes de promover a versão.
5. Os segredos ficam somente no ambiente de execução: URL e senha do banco, chaves de criptografia, e-mail do proprietário e token de configuração inicial.

Variáveis obrigatórias em cloud:

- `SPRING_PROFILES_ACTIVE=cloud`
- `DB_URL`, `DB_USER`, `DB_PASSWORD`
- `MAILFLOW_CREDENTIAL_KEY`
- `OWNER_EMAIL`
- `INITIAL_OWNER_SETUP_TOKEN` (somente até o primeiro cadastro)
- `APP_TIMEZONE=America/Sao_Paulo`

O host gratuito pode pausar ou recuperar recursos. A aplicação registra saúde e falhas sem vazar dados; indisponibilidade é tratada pela recuperação da fila, não por tentativas de manter o host artificialmente ativo.

## Testes e critérios de aceite

- Testes unitários para AES-GCM, integridade, chave inválida, rotação e ausência de segredos.
- Testes de integração PostgreSQL para claim concorrente, lease vencido, recuperação e envio tardio único.
- Testes web para impedir `/register`, restringir `/setup`, exigir HTTPS/cookies seguros no perfil cloud e bloquear acessos entre workspaces.
- Testes de migração Flyway a partir do esquema atual.
- Teste de deploy em ambiente separado com credenciais SMTP de teste, nunca e-mail real do proprietário.
- Varredura de dependências e revisão de configuração antes de publicar.

## Fora de escopo

- Pagamentos, assinaturas e planos.
- Cadastro público, convites, equipe e recuperação automática de senha.
- Anexos, edição de imagens, campanhas recorrentes e OAuth Gmail/Microsoft.
- Garantia de SLA, domínio próprio e suporte a múltiplas regiões.
