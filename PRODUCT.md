# MailFlow Local

## Produto e público

Aplicativo web local para organizar envios pessoais de e-mail no Windows. O público precisa de linguagem comum, não de formulários que exijam conhecer SMTP, IMAP ou portas.

## Trabalho principal

Escolher quem recebe, escrever uma mensagem, guardar um rascunho ou revisar e confirmar o envio. Agendamentos e repetições finitas ajudam a enviar no horário escolhido. A leitura opcional do Gmail reúne respostas relacionadas aos envios desta versão.

## Limites atuais

- Java 25, Spring Boot, Thymeleaf e PostgreSQL; acesso vinculado a 127.0.0.1.
- Uma conta proprietária local. Não é um serviço público multiusuário.
- Gmail usa senha de aplicativo quando permitida; OAuth e Outlook ainda não estão implementados.
- Rascunhos não expiram nem desaparecem após envio; somente o usuário os exclui.
- A leitura exige autorização separada. Não marca e-mails como lidos, move ou apaga mensagens no Gmail.
- Anexos, assinatura e pagamentos não fazem parte desta versão.
- Testes e prévias não acessam o banco pessoal, Gmail ou destinatários reais.

## Interface

Preservar a identidade escura existente. Navegação principal orientada a tarefas: início, novo envio, rascunhos e respostas. Configurações e funções secundárias ficam em Mais recursos. Priorizar texto legível, ações claras, estados vazios úteis e uso em telas pequenas.

## Futuro

O usuário pretende transformar o produto em um serviço por assinatura. Isso exige outra etapa de autenticação, isolamento multiusuário, autorização moderna, gestão de segredos e infraestrutura segura; não deve ser insinuado como já disponível.
