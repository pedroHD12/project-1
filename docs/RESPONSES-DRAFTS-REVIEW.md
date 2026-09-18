# Fechamento: respostas, rascunhos e interface

Data da verificação final: 18/09/2026.

## Resultado

- `mvn package`: concluído com código de saída zero.
- Surefire: 99 testes, 0 falhas, 0 erros e 0 ignorados, distribuídos em 22 suítes.
- Migrações V1 a V5: aplicadas em PostgreSQL temporário durante os testes.
- JAR local: `target/mailflow-local-0.1.0-SNAPSHOT.jar`.

## Comportamentos cobertos

- Rascunho incompleto não cria envio; persiste, recusa edição desatualizada e só é excluído por ação autenticada com CSRF.
- Revisar ou enviar uma cópia não remove o rascunho original.
- A leitura do Gmail começa desligada, exige autorização separada e não reutiliza a autorização depois de mudar a identidade da conta.
- Respostas são associadas por `Message-ID`, conta e endereço do destinatário, com deduplicação e cursor persistente.
- A caixa é aberta em modo somente leitura; falhas externas não avançam o cursor nem expõem detalhes sensíveis.
- HTML e MIME recebidos são reduzidos a texto com limites de tamanho, profundidade e quantidade de partes; anexos e conteúdo remoto não são importados.
- Um caractere NUL recebido não impede salvar as respostas seguintes nem avançar o cursor.
- MIME estruturalmente inválido, Base64 truncado ou codificação desconhecida gera um aviso seguro e não bloqueia as respostas seguintes; falhas reais de transporte continuam interrompendo o lote sem avançar o cursor.
- Um rascunho preserva destinatários selecionados mesmo quando eles estão fora dos primeiros 100 contatos exibidos.
- Quando a cota local é alcançada no meio de um lote, o prefixo que cabe é confirmado, o estado mostra `STORAGE_LIMIT` e a consulta pode continuar depois que o usuário exclui uma cópia local.

## Revisão visual

A prévia exclusiva de testes foi executada em `127.0.0.1:8083`, com banco, SMTP, IMAP, usuário e mensagens sintéticos. Foram verificadas as telas em desktop e em viewport móvel de 390 × 844 pixels.

O Verdict Pass independente do Impeccable classificou como resolvidos:

1. Painel da conta mais compacto, ajuda secundária recolhível e primeira resposta visível cedo no celular.
2. Ações explícitas **Ler resposta** e **Fechar**, mantendo a expansão nativa e exibindo corpo, envio original e exclusão local.

Disposição visual: `ship`, restrita a esses dois ajustes. As capturas ficam em `.impeccable/review/` e contêm somente dados de demonstração.

## Limites da validação

- Nenhum e-mail real foi enviado e nenhuma conta Gmail pessoal foi acessada.
- O banco pessoal do usuário não foi migrado; faça backup verificável antes de iniciar o novo JAR.
- A integração atual usa senha de aplicativo e IMAP do Gmail; OAuth, Outlook e anexos permanecem fora desta versão.
- Contatos, mensagens, rascunhos e respostas ficam em texto legível no PostgreSQL local. A aplicação protege a credencial de e-mail com DPAPI, mas o computador, o banco e os backups continuam dentro do limite de confiança.
- Testes e revisão reduzem risco, mas não comprovam ausência absoluta de vulnerabilidades nem entrega final por provedores externos.

## Condição para uso pessoal

Depois de criar e verificar um backup, iniciar o JAR aplica a migração V5 automaticamente. A confirmação final da integração depende do proprietário conectar explicitamente uma conta Gmail compatível e responder a uma mensagem enviada por esta versão.
