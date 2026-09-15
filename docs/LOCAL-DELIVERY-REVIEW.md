# MailFlow Local — revisão da entrega de envios

Data: 2026-09-14
Escopo: núcleo pessoal local, incrementos C/D. Não inclui SaaS, pagamentos, hospedagem pública, OAuth, recuperação de senha, campanhas em massa, anexos ou sequências condicionais.

## Resultado

O núcleo pessoal está implementado e empacotado: rascunho, personalização, prévia segura, teste para o próprio endereço, confirmação idempotente, envio imediato, agendamento único, repetição diária/semanal finita, pausa, retomada, cancelamento e histórico por destinatário.

## Evidências executadas

- `mvn -q package`: 77 testes, 0 falhas, 0 erros e 0 ignorados.
- Banco dos testes: PostgreSQL real temporário 14.22; migrations V1–V4, preservação do grafo legado, chaves entre workspaces e concorrência foram exercitadas. O banco pessoal não foi tocado.
- SMTP sintético: TLS obrigatório, identidade do servidor, autenticação rejeitada, aceite após DATA e perda de confirmação classificada como `UNKNOWN`. Nenhum Gmail ou destinatário real foi contatado.
- Navegador: Chromium, 1366×900, 390×844 e 320×740. Fluxos de cadastro/login, contato, conta fictícia, rascunho, HTML hostil, teste próprio, confirmação, automação de três ocorrências, pausa, retomada, cancelamento, histórico e menu por Escape passaram. Não houve erro de console, rolagem horizontal nem pedido externo da prévia.
- Artefato: `target/mailflow-local-0.1.0-SNAPSHOT.jar`, 67.050.539 bytes, SHA-256 `8108AB9568B369D467DC0AB89946032A69FDAE10186AAC9A175A7B2FC765B1CC`. A inspeção do arquivo confirmou V4, telas de entrega, fila/worker e jsoup 1.22.2.
- O servidor e o PostgreSQL temporários usados na prévia foram encerrados de forma graciosa.

## Cinco passagens de revisão

As tentativas de executar jurados em agentes separados foram interrompidas pelo limite de uso da ferramenta. Para não inventar independência, os pareceres abaixo são cinco passagens separadas do coordenador sobre o mesmo estado e as evidências acima; não são uma varredura formal de segurança externa.

### Helena — arquitetura

Veredito: aprovado para uso pessoal local. Pontos fortes: extensão incremental das tabelas, snapshots imutáveis por destinatário, fila persistente e transações curtas sem rede dentro do banco. O `delivery_worker_guard` serializa a reserva e limita escala, mas é uma escolha deliberada e simples para até 200 tentativas/dia. Não encontrou abstração inútil bloqueadora. Recomendação futura: substituir a trava global e o polling apenas se métricas de uma edição multiusuário justificarem. Prioridade baixa; classificação risco futuro; confiança alta.

### Ravi — experiência

Veredito: aprovado com ressalvas leves. O caminho usa linguagem comum, separa preparar/revisar/confirmar, mantém detalhes HTML opcionais e funciona nas três larguras observadas. O formulário fica longo quando HTML e repetição estão abertos; isso é fricção, não defeito, e os detalhes começam recolhidos no uso normal. Listas limitadas aos 100 registros recentes precisarão paginação se o uso crescer. Recomendações: medir uso antes de dividir a tela e adicionar paginação quando necessário. Prioridade baixa; classificação preferência/risco; confiança média-alta.

### Marta — segurança

Veredito: aprovado somente no limite local declarado. Pontos fortes: autenticação persistente, CSRF, escopo por workspace, confirmação explícita, destinatários separados, sanitização sem atributos/URLs, iframe isolado, TLS obrigatório, segredo via DPAPI, erros públicos genéricos e resultado SMTP incerto sem repetição automática. Durante a revisão, uma reserva antiga que já virara `UNKNOWN` ainda podia passar pela checagem anterior à rede; um teste primeiro reproduziu o problema e a fila agora exige e renova o token exato antes da conexão. Riscos conhecidos: contatos/corpos não são cifrados em repouso e o provedor manual não possui política anti-SSRF adequada à internet pública. Não são defeitos do modo localhost, mas bloqueiam hospedagem. Prioridade alta antes de SaaS; classificação risco; confiança alta.

### Caio — produto

Veredito: o objetivo pessoal principal está atendido sem antecipar cobrança ou arquitetura comercial. Contatos, modelos, conta de envio, envio/agendamento e histórico formam um fluxo útil; relatórios, importação, anexos e campanhas foram corretamente mantidos fora desta entrega. A configuração Gmail ainda depende de senha de aplicativo e o computador precisa ficar ligado, limitações explicadas na interface e no README. Recomendação: OAuth e um worker hospedado devem ser incrementos separados, se o produto virar assinatura. Prioridade média futura; classificação risco de produto; confiança alta.

### Lúcio — testes adversariais

Veredito: aprovado com cobertura forte para o escopo. Há testes de duplo clique/concorrência, isolamento, contatos bloqueados, horários, atrasos, pausa/cancelamento, limites persistentes, falhas SMTP seguras, perda de confirmação e workers antigos. O último teste novo prova que uma reserva recuperada como `UNKNOWN` não pode começar entrega de rede. Lacunas declaradas: somente Chromium, PostgreSQL 14.22 em vez do 17 do Compose, nenhum teste com Gmail real e nenhuma execução prolongada atravessando reinicialização física do Windows. Recomendações: testar o backup/restauração e um provedor real controlado antes de depender do sistema para mensagens importantes. Prioridade média operacional; classificação risco; confiança alta.

## Consensos e divergências

Há consenso de que o núcleo pessoal local está pronto para uso controlado e de que a fronteira localhost deve permanecer. Segurança e testes consideram OAuth, HTTPS, gestão de chaves e política de egressos bloqueadores para hospedagem; produto e arquitetura recomendam não antecipá-los agora. Ravi considera a extensão do formulário aceitável, mas sugere observar a usabilidade antes de expandir recursos.

## Riscos restantes e portão futuro

Os testes não provam ausência de vulnerabilidades nem entrega na caixa de entrada. Antes de publicar ou transformar em assinatura, será necessária uma decisão específica sobre: autenticação multiusuário/recuperação, HTTPS, segredos fora do host, OAuth, proteção de egressos, backup/restore e operação contínua. Essas recomendações não foram implementadas porque ampliariam materialmente o escopo local aprovado.
