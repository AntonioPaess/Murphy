# Murphy — proposta visual v0.1

Data: 2026-10-05. Estado: proposta para revisão; nenhuma tela implementada.

## Entrega e pendência

Quatro pranchas com 16 telas de 390 × 844, em SVG com textos/formas separados,
e prévias PNG. Não são um protótipo clicável nem uma biblioteca de componentes.
Os textos técnicos e as notas de função planejada são anotações de revisão;
na UI final, explicar estados em linguagem cotidiana.

Arquivo criado no [Figma](https://www.figma.com/design/25gjH4lJelpFg5jJEhWICm).
A integração atingiu o limite Starter antes de construir as telas. O login pelo
editor funcionou, mas a automação de importação não foi confiável: testes SVG
apareceram no canvas, sem confirmação das pranchas completas. Não considerar
esse arquivo a fonte de verdade do design completo ainda. Não houve upgrade
pago nem mudança de compartilhamento. Próximo passo: publicar os 16 frames com
texto editável, auto-layout, componentes reutilizáveis e ligações de navegação,
e validar a composição dentro do Figma.

## Direção visual

Verde floresta (#163D35), lima (#D9ED97), superfícies claras (#F5F6EF/#FFFFFF),
âmbar para pendências e vermelho (#B63632) para ajuda/falha. Estados usam texto
além da cor. Alvos principais: pelo menos 48 px; testar tamanhos ampliados,
leitores de tela, contraste, sol e uso com uma mão na implementação.

Não havia UI/fonte/Code Connect no núcleo compartilhado. A prévia usa Arial
sans-serif provisória; tipografia definitiva e equivalentes nativos ainda serão
decididos. Não adicionar dependência de fonte externa à operação offline.

## Telas e transições propostas

| Prancha | Telas | Caminho principal |
| --- | --- | --- |
| [Entrada](01-entrada.svg) | 01 boas-vindas; 02 permissões; 03 criar grupo; 04 encontrar grupo | Iniciar → autorizar Bluetooth → criar ou entrar |
| [Rede](02-rede.svg) | 05 grupo; 06 mesh; 07 peer; 08 link interrompido | Grupo → rede → peer → reconexão |
| [Mensagens/SOS](03-mensagens-sos.svg) | 09 conversa; 10 sem vizinhos; 11 confirmar SOS; 12 SOS em andamento | Escrever → tentativa; ou SOS → confirmação → status |
| [Histórico/ajustes](04-historico-ajustes.svg) | 13 histórico; 14 jornada; 15 ajustes; 16 encerramento | Evento → detalhes; grupo → ajustes → encerrar |

## Contrato de honestidade dos estados

- Mesh descentralizada; criar a sessão não cria um líder obrigatório.
- Diagrama é uma topologia ilustrativa, não GPS nem descoberta global entregue.
- `NoRoute`: nenhum vizinho elegível local; não prova destino global inacessível.
- Aceito pelo adapter não significa recebido pelo destino. ACK continua pendente.
- Reenvio: política padrão de 3 tentativas totais por mensagem/peer, 1 segundo
  entre tentativas elegíveis e capacidade de 128 pendências em memória.
- Peer desconectado não consome tentativa; sem-rota não entra na fila atual.
- Histórico/fila atuais em memória; não prometer restauração após fechar o app.
- SOS é planejado, limitado à comunicação entre peers. Não aciona serviço de
  emergência, não garante entrega e não inclui localização automática.
- Grupos, identidade, chat, segurança, descoberta e adapters BLE físicos ainda
  precisam ser construídos/validados; não são habilitados por este design.

## Verificação deste incremento

SVGs renderizados localmente e pranchas inspecionadas. Não há imagens de UI
achatadas dentro dos SVGs. Conteúdo ilustrativo identificado. Corrigida a rede
degradada para manter o caminho alternativo em verde. Sem mudança no código
de rede; a evidência anterior continua sendo 25 testes JVM em conexões fake,
não validação de Bluetooth físico. Figma e acessibilidade interativa pendentes.
