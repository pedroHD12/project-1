# Implantação privada: Oracle VM + Supabase

Esta é uma implantação para **uma única pessoa**, não um serviço SaaS público. Faça cada passo em uma VM nova, com um domínio próprio e contas que você controla.

## Antes de iniciar

1. Crie um projeto Supabase e um banco PostgreSQL. Copie a URL com `sslmode=verify-full`; o MailFlow recusa conexões sem essa verificação TLS.
2. Crie uma VM Oracle Linux/Ubuntu com IP público e instale Docker Engine + Compose plugin. Libere no firewall da Oracle somente TCP 80 e 443. Não libere 8080, PostgreSQL ou SSH para toda a internet.
3. Crie um registro DNS `A` para o domínio apontando ao IP da VM. Espere a propagação antes de iniciar Caddy; ele obtém o certificado HTTPS automaticamente.
4. Faça `git clone` do seu repositório privado na VM e copie `.env.cloud.example` para `.env.cloud`. Preencha valores próprios. O arquivo real nunca deve ir para Git, backup público ou captura de tela.

## Chaves e primeiro acesso

Na VM, gere os dois segredos abaixo e guarde-os em um gerenciador de senhas:

```bash
openssl rand -base64 32   # MAILFLOW_CREDENTIAL_KEY_V1
openssl rand -base64 48   # INITIAL_OWNER_SETUP_TOKEN
```

Use seu endereço em `OWNER_EMAIL`. Suba a aplicação:

```bash
docker compose --env-file .env.cloud -f deploy/docker-compose.cloud.yml up -d --build
docker compose --env-file .env.cloud -f deploy/docker-compose.cloud.yml ps
curl -fsS https://SEU-DOMINIO/actuator/health
```

Abra `https://SEU-DOMINIO/setup`, informe o e-mail exatamente igual a `OWNER_EMAIL`, defina a senha e use o token de configuração. Depois confirme que `/setup` e `/register` respondem 404. Apague `INITIAL_OWNER_SETUP_TOKEN` de `.env.cloud`, execute `docker compose ... up -d` novamente e mantenha a cópia do token somente no seu cofre; o token não é mais necessário depois do primeiro acesso.

## Operação segura

- A aplicação só fica acessível por Caddy em HTTPS. O container Java não publica porta no host.
- Configure o SMTP pelo site e use uma senha de aplicativo, não sua senha comum. As credenciais ficam cifradas por AES-GCM no ambiente hospedado.
- Para trocar a chave, coloque a chave anterior em `MAILFLOW_CREDENTIAL_KEY_V0` e a nova em `MAILFLOW_CREDENTIAL_KEY_V1`. Abra/teste cada conta SMTP; então remova V0 e reinicie.
- Consulte logs sem imprimir as variáveis: `docker compose --env-file .env.cloud -f deploy/docker-compose.cloud.yml logs --tail=100 app`.
- Atualize com `git pull` e `docker compose --env-file .env.cloud -f deploy/docker-compose.cloud.yml up -d --build`. Faça backup antes de atualizar.

## Backup e restauração

Use o mecanismo de backup do Supabase e teste uma restauração em outro projeto/banco antes de confiar nele. Os dados do MailFlow incluem contatos, conteúdo e histórico; proteja também os backups. Uma restauração não recupera a chave de criptografia se ela foi perdida, portanto guarde `MAILFLOW_CREDENTIAL_KEY_V1` em um cofre separado.

Não use este guia para hospedar em computador doméstico, expor a porta do Java, compartilhar a conta ou criar contas para terceiros. Esses cenários exigem autenticação multiusuário, recuperação de conta, monitoramento e uma revisão de segurança dedicada.
