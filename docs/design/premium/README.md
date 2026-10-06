# Murphy — direção premium v0.2

2026-10-05 · 16 telas conceituais · Android como referência de interação.
Não há alteração de UI funcional, adapters BLE ou protocolo neste incremento.

## Intenção

O usuário pediu acabamento mais premium, usando Revolut como referência.
A [apresentação oficial do Revolut 10](https://www.revolut.com/blog/post/revolut-10/)
foi consultada pela simplificação da tela inicial e acesso rápido às funções.
Não é uma cópia: sem logos, imagens, fontes proprietárias ou telas do Revolut.

## O que mudou

- Base clara neutra e grafite, com azul profundo (#224BFA) como destaque.
- Métrica principal grande, com o contexto “vizinhos diretos”; nada de métricas
  de alcance, qualidade ou entrega que o núcleo ainda não consegue sustentar.
- Mais espaço, cartões suaves de 22–30 px e ações circulares de 54–56 px.
- Navegação com ícones vetoriais e rótulos; não depende só de cor.
- Boas-vindas e pedido de ajuda ativo em grafite. SOS vermelho, sem diluir
  alertas na aparência premium. Gradientes decorativos não cobrem texto crítico.
- Explicações de implementação passam às notas de revisão; mensagens do app
  são curtas e em português. A ausência de confirmação permanece explícita.

## Pranchas

![Visão premium](00-premium-overview.svg.png)

| Prancha | Telas |
| --- | --- |
| [Entrada](01-entrada.svg.png) / [SVG](01-entrada.svg) | Boas-vindas, permissões, criar grupo, encontrar grupo |
| [Rede](02-rede.svg.png) / [SVG](02-rede.svg) | Grupo, rede mesh, peer, conexão interrompida |
| [Mensagens/SOS](03-mensagens-sos.svg.png) / [SVG](03-mensagens-sos.svg) | Conversa, sem vizinhos, confirmar SOS, SOS ativo |
| [Histórico/ajustes](04-historico-ajustes.svg.png) / [SVG](04-historico-ajustes.svg) | Histórico, jornada, ajustes, encerramento |

## Tipografia e interação

Prévia: Helvetica Neue (disponível no ambiente de renderização), com Arial como
fallback. É uma escolha visual provisória, não uma fonte instalada no Android.
Na implementação, comparar com a família nativa Android ou uma alternativa
licenciada e embarcada para funcionar offline. Hierarquia: títulos 30–44 px,
corpo 14–17 px e métrica principal 88 px.

Na implementação: alvos interativos mínimos de 48 dp, estados pressionado/foco,
leitores de tela, ampliação de fontes, contraste sob sol e área segura/teclado.
Textos secundários de 11–13 px da prévia precisam de revisão no aparelho; esta
prancha não equivale a uma certificação de acessibilidade.
Animações propostas: transições discretas de 160–220 ms, sem bloquear SOS e
respeitando movimento reduzido. Nada disso foi implementado ainda.

## Limites preservados

Grupos, descoberta global, identidade, chat e SOS permanecem planejados.
Enviar/aceitar no transporte não confirma o destino. Histórico e fila atuais
ficam em memória; ausência de vizinhos não prova destino global inacessível.
SOS não aciona serviço de resgate, não garante entrega e não leva localização
automática. Não há líder obrigatório. Nenhuma promessa de criptografia pronta.

## Figma e verificação

[Arquivo existente](https://www.figma.com/design/25gjH4lJelpFg5jJEhWICm).
Nova consulta confirmou limite Starter; nenhuma escrita Figma foi feita nesta
revisão. Prévia local não é um protótipo Figma publicado ou clicável. Não houve
upgrade pago. A publicação de frames/componentes editáveis continua pendente.

Cinco pranchas SVG renderizadas em PNG e inspecionadas; quatro cobrem as 16
telas e uma destaca grupo/rede/chat/SOS. Formas, textos e ícones separados,
sem UI rasterizada dentro do SVG. Corrigidas curvas dos links mesh durante a
revisão visual. Código compartilhado não mudou; testes JVM não reexecutados.
