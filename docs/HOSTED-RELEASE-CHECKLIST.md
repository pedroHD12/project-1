# Checklist de publicação privada

Preencha esta lista antes de abrir o MailFlow hospedado para uso real.

- [ ] O domínio aponta para a VM e o certificado HTTPS está válido.
- [ ] A VM libera somente 80/443; Java, PostgreSQL e SSH não estão publicamente expostos.
- [ ] `DB_URL` usa PostgreSQL com `sslmode=verify-full`.
- [ ] `MAILFLOW_CREDENTIAL_KEY_V1` foi gerada com 32 bytes aleatórios e guardada fora da VM.
- [ ] Há um backup recente do banco e uma restauração foi testada em outro banco.
- [ ] O primeiro acesso foi feito em `/setup` com o e-mail definido como proprietário.
- [ ] Depois do primeiro acesso, `/setup` e `/register` respondem 404 e o token de configuração foi removido da VM.
- [ ] A conta SMTP foi testada com um destinatário descartável e uma senha de aplicativo.
- [ ] Um agendamento intencionalmente atrasado foi verificado como `SENT_LATE`, sem uma segunda transmissão.
- [ ] `/actuator/health` responde sucesso e `/actuator/env` não está exposto.
- [ ] Os logs do container foram verificados e não mostram senha, token, chave, endereços de listas ou conteúdo das mensagens.
- [ ] Atualizações foram aplicadas primeiro em backup/teste e o comando de retorno foi documentado.

Este checklist não torna a aplicação apropriada para SaaS ou para compartilhar acesso. Recuperação de conta, multiusuário, suporte e monitoramento profissional continuam fora do escopo.
