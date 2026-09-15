# MailFlow — revisão da fundação local

Data: 9 de setembro de 2026. Escopo: incrementos de fundação, navegação e configuração de e-mail; **não** é a entrega de automações, SaaS ou hospedagem.

## Resultado

Cadastro/login persistentes, isolamento de dados, interface simplificada e configuração de envio foram implementados e verificados. O pacote executável foi gerado com **45 testes, zero falhas, zero erros e zero testes ignorados**.

Não há certificação de segurança nem garantia de ausência de vulnerabilidades. A varredura profunda mencionada em sessões anteriores não possui conclusão confirmada e não foi usada como evidência. Este documento é a revisão local do incremento, não um relatório final do fluxo dedicado Codex Security Deep Scan.

## 1. Escopo e evidências

- Código de autenticação, filtros, controllers, serviços, repositórios, configuração, templates, JavaScript, migrações e testes.
- H2 para integração rápida; PostgreSQL **14.22 real e descartável** para migrações/concorrência e isolamento com usuários/workspaces persistidos.
- Migração da V2 para V3: snapshots de todos os campos/IDs das **19 tabelas**, backfill sem nulos, **15 relações** entre workspaces e preservação de CASCADE, SET NULL e RESTRICT.
- Cadastro inicial concorrente; lockout; CSRF; IDs estrangeiros; preservação/remoção de credenciais; STARTTLS obrigatório contra servidor sintético sem TLS; erros externos sem detalhes.
- Quatro cadastros simultâneos por cenário em PostgreSQL: um sucesso, três respostas de validação e um único registro, para contatos e contas de envio.
- Navegador Chrome real, via Playwright, com banco temporário e dados fictícios.
- Cinco agentes revisores independentes, cada um sem ler os demais pareceres. Duas rodadas: diagnóstico e reavaliação dirigida das correções. Os revisores fizeram leitura estática/visual; as execuções abaixo foram realizadas pelo coordenador.

Não foram inspecionados dados pessoais, enviadas mensagens reais, alterado o banco pessoal, iniciado serviço público, auditadas CVEs por scanner especializado ou testados provedores reais. O Compose usa PostgreSQL 17; essa imagem não foi executada nesta verificação.

## 2. Cinco opiniões

### Helena — arquitetura

**Veredito inicial:** aprovado com ressalvas, condicionado à verificação mais ampla da V3. **Final:** aprovado para o incremento local.

Pontos fortes: transação de primeiro proprietário; workspace vindo do principal; consultas por proprietário; FKs compostas; validação do segredo no servidor. Evidências: AccountStore, CurrentWorkspace, serviços de domínio e V3.

Falhas observadas encerradas: cobertura inicial da migração limitada a três tabelas (alta, confiança alta) e README de autenticação antigo (média, confiança alta). LegacyGraphMigrationTest e PostgresFoundationTest agora cobrem o grafo completo e o fluxo real de persistência.

Remanescentes: JpaRepository expõe métodos globais não usados pelos consumidores atuais (média, risco futuro); preparação do DTO repetida em controller/serviço (baixa, manutenção); índices com prefixos redundantes (baixa, preferência fundamentada). Adaptadores de entrega não usados são antecipação de baixo valor imediato.

Recomendação: preservar a implementação simples atual, mas reforçar a API de acesso aos dados antes de novos consumidores multiusuário. Confiança alta; nenhum teste reexecutado pela jurada.

### Ravi — experiência

**Veredito inicial:** aprovado com ressalvas. **Final:** aprovado.

Pontos fortes: português direto, sidebar consistente, disclosure nativo no celular, instrução de primeiro uso e ausência de links fictícios. Inspeção: templates/CSS/JS e cinco capturas desktop/mobile.

Encerrados: aviso tardio sobre falta de recuperação; ambiguidade entre configuração automática e autorização do Gmail; campos de cadastro sem associação de erro; mensagens de ação sem semântica acessível. O coordenador também mediu o contraste dos botões: de 2,98:1 para 6,42:1 após correção.

Discordância preservada: Ravi preferiria Meu e-mail no primeiro nível; manteve-se em Mais recursos por solicitação do usuário, com acesso direto no onboarding. Baixa prioridade, preferência, confiança alta. O CTA de modelo compete com a ordem linear do onboarding (baixa, preferência).

Ressalva localizada: a validação de conteúdo do formulário de modelos ainda não tem role=alert (baixa, risco). HTML opcional sem visualização agrega complexidade, mas não justifica remover dados ou funcionalidade neste incremento.

Recomendação: priorizar o próximo fluxo real de envio antes de cosmética adicional. Leitor de tela dedicado não foi executado; resultados do Chrome foram evidência fornecida ao revisor.

### Marta — segurança

**Veredito inicial:** aprovado com ressalvas. **Final:** aprovado para uso local.

Pontos fortes: loopback e verificação de Host, CSRF/CSP, BCrypt, lockout, isolamento de domínio, proteção DPAPI, TLS obrigatório e erros públicos fixos. Não identificou bypass ou IDOR ativo no escopo lido.

Encerrados: guia antigo de acesso (média, defeito); retenção de credencial ao retirar autenticação (baixa, defeito); configuração SMTP global dormente insegura (baixa agora, risco futuro); mensagens de exceções internas na página 404 (baixa, risco).

Remanescentes: SmtpEmailGateway continua dormente e deverá convergir para transporte seguro antes de ser ativado; supressão futura precisa comparar bloqueados sem distinção de caixa; logging sanitizado estruturado será preferível ao logger JDBC desativado. Confiança alta. Destinos manuais internos são capacidade intencional do proprietário local, mas exigirão controle de egresso/SSRF em uma edição pública.

Recomendação: não publicar nem conectar o adaptador antigo a novas entregas sem um novo incremento de segurança. Não houve validação de provedor/certificado real pela jurada.

### Caio — produto

**Veredito inicial:** aprovado com ressalvas e handoff operacional pendente. **Final:** aprovado com ressalvas menores.

Pontos fortes: escopo honesto; navegação só com destinos reais; conta proprietária compreensível; configuração mais simples; sem antecipar cobrança/equipes.

Encerrados: README incompatível (alta, defeito); ausência de orientação de backup (alta, risco operacional); rótulo ATIVA sugerindo conexão válida (média, semântica); Outlook prometido sem implementação adequada (média, desvio de escopo). A documentação agora registra o adiamento e a interface usa Habilitada/Pausada.

Residual principal: não há recuperação da única senha. Backup preserva essa senha, não remove autenticação (média, risco aceito e explicitamente informado). O limite UTF-8 pode rejeitar frases com muitos emojis/acentos antes de 72 caracteres (baixa, usabilidade).

Recomendação: recuperação offline segura antes de distribuição ampla. HTML, Quartz e tabelas futuras não devem ser apresentados como automação entregue. Confiança alta; somente leitura.

### Lúcio — testes adversariais

**Veredito inicial:** reprovado com ressalvas. **Final:** aprovado com ressalvas.

Pontos fortes: corrida de primeiro cadastro serializada; limite real do BCrypt; filtros de workspace; presets no servidor; TLS e FKs.

Encerrados com regressões executadas: sucessos esgotavam o limite global de login (alta, defeito); duplicatas concorrentes podiam virar erro de servidor (alta, inicialmente risco, depois reproduzido); migração pouco exercitada; isolamento apenas em H2 sem workspaces persistidos; README e JAR antigos. Novos testes reproduziram os problemas antes das correções.

Remanescentes: orçamento global de falhas permite bloqueio breve por um processo local (baixa, risco); busca aceita curingas e não limita explicitamente q (baixa); duas abas podem sobrescrever edições por falta de versionamento (média, risco documentado). Sem evidência de falha alta residual no incremento local. Confiança alta, exceto concorrência de edições (média).

Recomendação: manter as regressões PostgreSQL e tratar versionamento/limites ao ampliar o produto. Inspeção dos XMLs confirmou 45 casos aprovados; o revisor não executou testes.

## 3. Resumo por jurado

- Helena: migração e acesso a dados suficientemente verificados para o incremento; atenção aos futuros consumidores de repositórios.
- Ravi: interface aprovada; conserva preferência por outra posição de Meu e-mail e uma ressalva acessível localizada.
- Marta: aprovada somente para uso local; nenhuma autorização de exposição pública.
- Caio: entrega operacional honesta; recuperação de senha permanece uma lacuna explícita.
- Lúcio: falhas e riscos altos iniciais foram encerrados; edições simultâneas e limites de busca ficam registrados.

## 4. Consensos e discordâncias

Todos concordaram em corrigir documentação e segurança/correção antes de ampliar automações. Os achados altos iniciais foram encerrados na segunda rodada.

Não houve unanimidade estética: a posição de Meu e-mail e o CTA de modelos continuam escolhas de produto, não falhas de autorização. Recuperação de conta é mais urgente para distribuição ampla do que para a fase local supervisionada. Não se converteu essa ressalva em uma recuperação improvisada insegura.

## 5. Verificação executada

Comando final: `.local-tools/apache-maven-3.9.11/bin/mvn.cmd -q package` — exit 0.

Artefato: `target/mailflow-local-0.1.0-SNAPSHOT.jar`, 66.484.664 bytes.

SHA256: `D242BAC9D6676093EE3F206506702DB9AD67548085BED186F35B9BE1F1FF34BD`.

O JAR contém AccountStore e a V3; não contém o helper de prévia com PostgreSQL temporário. Há avisos de dependências sobre APIs do Java que serão restringidas em versões futuras, sem falhas no Java 25 usado.

### QA da interface

Fluxo: cadastrar proprietário → entrar → criar contato/modelo → configurar e editar Meu e-mail → validar alteração de servidor → navegar pelo menu no celular → sair.

Ambiente: `http://localhost:8080`; Chrome headless via Playwright já disponível. O plugin Browser não estava disponível. O Edge encerrou antes da inicialização; a mesma verificação passou no Chrome, sem instalar dependências de navegador.

| Verificação | Resultado |
|---|---|
| URL/título corretos e conteúdo significativo | Passou |
| Página vazia ou sobreposição de erro | Não observada |
| Console JavaScript | Zero erros nos fluxos executados |
| Cadastro, login e logout | Passaram |
| Cadastro de contato e modelo | Passou |
| Gmail automático e seleção manual | Passaram, sem tráfego SMTP |
| Senha não reapresentada; mudança de destino exige nova senha | Passou |
| Menu, Escape e devolução de foco | Passaram |
| Desktop 1366×900, celular 390×844, largura 320 | Sem overflow horizontal |
| Contraste do botão principal | 6,42:1 |

Foram duas rodadas visuais, com correções agrupadas entre elas. A prévia e o banco temporário foram encerrados normalmente; a porta 8080 ficou livre. Os testes não mantiveram um site pessoal configurado nem migraram seu banco.

Capturas finais ficam fora do repositório, na pasta de visualizações desta tarefa: mailflow-register-desktop.png, mailflow-home-desktop.png, mailflow-email-desktop.png, mailflow-email-mobile.png e mailflow-home-narrow.png.

## 6. Recomendações e portão de decisão

| Prioridade | Próximo item | Impacto / esforço / dependência |
|---|---|---|
| Antes de uso amplo | Recuperação offline segura da conta | Evita perda de acesso; esforço médio; exige desenho próprio |
| Antes de enviar a contatos | Entrega por conta, sanitização, revisão e idempotência | Segurança/correção altas; depende do incremento C |
| Antes de automatizar | Fila persistente, retentativas, limites e bloqueados case-insensitive | Correção alta; depende da entrega estável |
| Antes de hospedar | HTTPS, OAuth, autorização multiusuário, egresso, operação e auditoria especializada | Risco alto; exige implantação própria |
| Evolução local | Versionamento de edição, limites da busca e erro acessível residual | Melhoria de robustez; não bloqueia o incremento atual |

As correções desta etapa foram cobertas pela autorização expressa de desenvolvimento e correção do usuário. Não houve contratação, publicação, envio real, alteração de segredos pessoais ou migração do banco pessoal. Essas ações não são autorizadas por esta revisão.

Superpowers orientou o ciclo teste-falha-correção e a verificação final. Senior Dev Orchestrator manteve o escopo e as evidências. Impeccable orientou linguagem comum, disclosure dos detalhes, responsividade, foco e contraste; seu carregador automático falhou por permissão de cache, então foi usado o contexto existente. Higgsfield não gerou imagens: ferramentas de geração não estavam disponíveis e esta interface não dependia de imagens novas.
