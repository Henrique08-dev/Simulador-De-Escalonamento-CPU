# 📘 Guia de Estudo — Revisão de Sistemas Operacionais

> Guia elaborado a partir da análise da Lista de Exercícios de Revisão de Sistemas Operacionais.
> Cada seção traz os **conceitos-chave**, a **intuição** e dicas de **como responder** as questões correspondentes.

---

## 1. As duas visões do Sistema Operacional

O SO pode ser analisado sob duas perspectivas complementares:

### 1.1 Máquina estendida (abstração)
O SO esconde a complexidade do hardware, oferecendo às aplicações uma interface mais simples e conveniente.
- Abrir um arquivo **sem conhecer** setores, trilhas ou controladora do SSD → visão de máquina estendida.
- Enviar dados à impressora **sem conhecer** os detalhes eletrônicos do dispositivo → máquina estendida.

### 1.2 Gerenciador de recursos (multiplexação)
O SO administra recursos limitados e compartilhados entre múltiplos usuários/processos.
- **Cinco processos prontos** disputando **uma única CPU** → gerenciador de recursos (CPU é multiplexada no tempo).
- **Vários programas carregados na memória** ao mesmo tempo → gerenciador de recursos (memória é multiplexada no espaço).

> 💡 **Dica para a questão:** pergunte-se: *"o foco é esconder complexidade (abstração) ou dividir um recurso escasso (gerenciamento)?"*

---

## 2. Multiprogramação, Processo e Pseudoparalelismo

### 2.1 Processo
Um **processo** é um **programa em execução**, junto com seu contexto:
- contador de programa (PC), registradores, pilha, dados, arquivos abertos, etc.

### 2.2 Programa ≠ Processo
- **Programa**: entidade **passiva**, um arquivo estático armazenado em disco (código).
- **Processo**: entidade **ativa**, uma instância em execução do programa.

### 2.3 Pseudoparalelismo e multiplexação no tempo
- Com CPUs limitadas, o SO alterna rapidamente entre processos (**multiplexação no tempo da CPU**).
- Como a troca é muito rápida (dezenas de milissegundos), o usuário tem a **ilusão** de que tudo executa ao mesmo tempo → **pseudoparalelismo**.
- Ex.: navegador + editor + player + IDE — apenas uma CPU real; cada processo recebe uma **fatia (quantum)** de CPU por vez.

### 2.4 Duas execuções do mesmo programa
- Abrir o mesmo navegador duas vezes: **1 programa** (um arquivo no disco), **2 processos** (duas instâncias independentes).
- Os processos **não** possuem necessariamente o mesmo estado (um pode estar pronto, outro bloqueado aguardando rede).
- O SO mantém informações separadas (PC, registradores, pilha, PCB — *Process Control Block*) porque cada execução tem histórico e contexto próprios.

---

## 3. Estados de um Processo

Modelo de **três estados**:

| Estado | Significado |
|---|---|
| **Executando (running)** | Usando a CPU no momento. |
| **Pronto (ready)** | Tudo preparado para executar; aguardando apenas a CPU ficar livre. |
| **Bloqueado (blocked/esperando)** | Aguardando um evento externo (E/S, dados da rede); **não pode executar mesmo que a CPU esteja livre**. |

### Transições importantes
- Executando → **Pronto**: quantum esgotado (preempção) ou processo de maior prioridade chega.
- Executando → **Bloqueado**: processo solicita E/S e precisa esperar.
- Bloqueado → **Pronto**: o evento esperado ocorreu (dados chegaram) — **não vai direto a executando**! Precisa passar pela fila de prontos, pois outro processo pode estar usando a CPU.
- Pronto → Executando: o escalonador escolhe o processo.

> ⚠️ **Ponto clássico de prova:** quando o dado chega para um processo bloqueado, ele vai para **pronto**, e só executa quando o escalonador o selecionar. Bloqueado → Executando **não existe** nesse modelo.

### Aplicação: pipe `cat | grep`
Se `grep` está pronto para processar, mas o pipe ainda está vazio:
- Deixar `grep` **girando na CPU** desperdiça processamento (ocupação **ociosa/busy-waiting**).
- O SO coloca `grep` no estado **bloqueado**; quando `cat` escrever dados no pipe, `grep` vai a **pronto** e poderá ser escalonado novamente.

---

## 4. Criação de Processos: `fork()` e `execve()`

### 4.1 `fork()`
- Cria uma **cópia** do processo chamador (pai), gerando um **processo filho**.
- Pai e filho executam o **mesmo código**, mas com PID e espaço de endereçamento distintos.
- O valor de retorno difere: pai recebe o PID do filho; o filho recebe **0** (permite distinguir quem é quem no código).

### 4.2 `execve()`
- **Substitui** a imagem do processo: carrega um novo programa no processo atual, descartando o código anterior.
- É o que transforma a "cópia do pai" em outro programa (ls, grep, etc.).

### 4.3 Por que ambas são necessárias?
Uma cópia do processo pai executaria **o mesmo programa**. Para um **shell** executar comandos diferentes, é preciso:
1. `fork()` → criar o processo filho (isola o shell);
2. `execve()` → carregar o programa do comando no filho.

---

## 5. Estrutura de um Shell

Fluxo: exibir prompt → ler comando → **criar processo filho** → executar comando → **esperar o filho** → exibir prompt novamente.

**a) Por que executar num processo filho?**
- O **shell (pai) precisa permanecer intacto** para exibir o próximo prompt. Se executasse o comando em seu próprio processo, o comando sobrescreveria a imagem do shell (via `execve`) e o shell morreria.
- Isolamento: erros, sinais e término do comando não afetam o pai.

**b) Se o shell não aguardasse o término do filho?**
- O prompt reapareceria **antes do comando terminar**; saída do comando se misturaria com o próximo prompt/comandos; o usuário não saberia quando o comando realmente acabou.

**c) Quem exibe o próximo prompt?**
- O **processo pai (shell)**, que sobrevive e continua o ciclo.

---

## 6. Modos de Execução, Chamadas de Sistema e Proteção

### 6.1 Modo usuário × modo núcleo (kernel)
- **Modo usuário**: instruções comuns; sem acesso direto ao hardware.
- **Modo núcleo**: instruções privilegiadas; acesso total a hardware, memória e dispositivos.

### 6.2 Caminho de uma leitura de arquivo
1. Programa em **modo usuário** chama a biblioteca (`read()`).
2. A biblioteca dispara uma **chamada de sistema** (syscall).
3. Ocorre um **trap** (interrupção de software): o processador salva o contexto e muda para **modo núcleo**.
4. O **SO (kernel)** executa o serviço: aciona o controlador do disco, transfere os dados.
5. Ao final, ocorre o retorno da chamada; o processo volta a **modo usuário** com os dados.

### 6.3 Por que proibir acesso direto ao hardware?
Se qualquer aplicação executasse instruções privilegiadas:
1. **Falhas de um programa travariam o sistema inteiro** (sem isolamento — o programa poderia corromper memória de outros ou do próprio SO).
2. **Violação de segurança**: qualquer programa poderia ler/escrever arquivos e dispositivos de outros usuários.
3. **Concorrência caótica**: dois programas acessando a impressora/disco ao mesmo tempo gerariam resultados corrompidos (é o SO que serializa e sincroniza o acesso a recursos).

> 💡 **A afirmação "ficaria mais rápido" é incorreta:** o custo da chamada de sistema (trap + troca de modo) é desprezível perto dos benefícios de confiabilidade e segurança, e o SO pode otimizar o acesso ao hardware (ex.: reordenar requisições de disco).

---

## 7. Modelo de Utilização da CPU em Multiprogramação

### 7.1 A fórmula
Se cada processo passa uma fração **p** do tempo esperando por E/S, e há **n** processos na memória:

```
P(todos bloqueados) = pⁿ        (probabilidade de CPU ociosa)

U = 1 − pⁿ                       (utilização da CPU)
```

### 7.2 Exemplos resolvidos

**Ex. A — n = 4, p = 0,7:**
- P(todos bloqueados) = 0,7⁴ = **0,2401** → 24,01%
- U = 1 − 0,2401 = **0,7599** → ≈ 76% de utilização
- CPU ociosa ≈ **24%**

**Ex. B — RAM de 8 GB, SO ocupa 2 GB, cada processo 1 GB, p = 0,8:**
- a) Grau de multiprogramação = (8 − 2) / 1 = **6 processos**
- b) U = 1 − 0,8⁶ = 1 − 0,262144 = **0,7379** → ≈ 73,8%
- c) +4 GB → 12 GB: (12 − 2) / 1 = **10 processos**
- d) U = 1 − 0,8¹⁰ = 1 − 0,107374 = **0,8926** → ≈ 89,3%
- e) Mais memória permite **mais processos simultâneos**; com pⁿ menor, a chance de todos estarem bloqueados cai e a CPU fica menos ociosa.

**Ex. C (desafio) — n = 5, p = 0,6:**
- a) Todos bloqueados: 0,6⁵ = 0,07776 → ≈ **7,8%** do tempo
- b) CPU ocupada: 1 − 0,07776 = **92,2%**
- Significado: mesmo com 5 processos e muita E/S, quase sempre há **pelo menos um processo pronto**; a CPU só fica ociosa quando todos os 5 se encontram bloqueados simultaneamente (evento raro).

### 7.3 Por que nunca chega a 100% na prática?
- Matematicamente, U = 1 − pⁿ **nunca** atinge 1 para p > 0 (basta que exista alguma chance, por menor que seja, de todos bloquearem).
- Conceitualmente: processos reais passam por fases; sempre há instantes em que **todos** aguardam E/S, trocas de contexto consomem CPU, e nem todo processo está na memória pronto para rodar.

---

## 8. Escalonamento de CPU

### 8.1 Conceitos-chave
- **Escalonamento**: escolher qual processo pronto usará a CPU.
- **Não preemptivo**: o processo **só libera a CPU** ao terminar ou ao se bloquear (voluntariamente).
- **Preemptivo**: o SO **pode retirar** a CPU de um processo (quantum esgotado, prioridade, chegada de processo mais curto/importante). Requer **interrupção de relógio (timer)**.

### 8.2 Classificação
| Algoritmo | Tipo | Justificativa |
|---|---|---|
| **FCFS** (First-Come, First-Served) | Não preemptivo | Quem chega primeiro executa até o fim. |
| **SJF** (Shortest Job First), forma básica | Não preemptivo | Menor tempo de CPU já na fila é escolhido; executa até o fim. |
| **Round Robin** | **Preemptivo** | Cada processo recebe um quantum; ao esgotá-lo, volta ao fim da fila de prontos. |

> ⚠️ **Round Robin e o relógio:** a preempção só é possível porque o timer gera **interrupções de relógio**; ao final de cada quantum, a interrupção devolve o controle ao SO, que troca o processo.

### 8.3 Métricas
- **Tempo de espera** = tempo na fila de prontos = (término − chegada) − duração de CPU.
- **Tempo médio de espera** = soma das esperas ÷ nº de processos.
- Diagrama de **Gantt** representa quem ocupa a CPU em cada intervalo.

### 8.4 Exercício FCFS resolvido (modelo)
Processos: P1(0,8), P2(1,4), P3(2,2), P4(3,1), P5(5,3) — (chegada, CPU).

- Ordem: P1 → P2 → P3 → P4 → P5 (chegada)
- Esperas: P1 = 0; P2 = 8−1 = 7; P3 = 12−2 = 10; P4 = 14−3 = 11; P5 = 15−5 = 10
- Média = (0+7+10+11+10)/5 = **7,6**

### 8.5 Armadilha do SJF com tempos de chegada
- **Não se pode** ordenar todos os processos pelo tamanho antes de calcular: um processo que chega **depois** não estava na fila quando a CPU ficou livre.
- Correto: a cada instante em que a CPU desocupa, escolhe-se o **menor dentre os que já chegaram**.
- SJF geralmente reduz o tempo médio de espera em relação ao FCFS, **mas** pode causar **starvation** (espera indefinida) de processos longos.

### 8.6 Round Robin — exemplo com quantum = 2
Processos: P1(CPU 7), P2(5), P3(3), P4(6), todos chegando em 0.
Sequência: P1, P2, P3, P4, P1, P2, P3, P4, P1, P2, P4, P1, P2, P4, P1
- Após cada quantum, o processo **perde a CPU** se restar trabalho e volta ao fim da fila.
- No final, cada processo termina quando seu restante chega a 0 (processos curtos/pares como P3 terminam mais cedo).
- Espera de cada processo = (instante de término − chegada) − CPU total.

### 8.7 Quantum × custo de troca de contexto (1 ms)
- **Quantum pequeno (4 ms):** proporcionalmente mais trocas de contexto → mais **overhead** (25% do tempo em troca, no limite), mas **melhor tempo de resposta** para interativos.
- **Quantum grande (100 ms):** poucas trocas (overhead ~1%), mas processos interativos podem esperar muito.
- **Aumentar o quantum indefinidamente não resolve:** com quantum → ∞, o Round Robin degenera e se comporta como **FCFS** — o primeiro processo monopoliza a CPU até terminar, perdendo a ideia de justiça/paridade entre processos.

---

## 9. Questão Integradora — CPU única, múltiplas tarefas

Cenário: chamada de vídeo, compilação, atualização, navegador e processo aguardando rede — **1 núcleo**.

Vocabulário esperado na resposta:
- Cada tarefa é um **processo** com **estado** próprio: a chamada de vídeo e a compilação disputam **pronto/executando**; quem aguarda a rede está **bloqueado**.
- O SO faz **escalonamento** com **prioridades** (vídeo interativo > atualização em background).
- **Preempção** garante que processos de alta prioridade não esperem (com quantum, o vídeo não trava).
- **Interrupções** (timer, conclusão de E/S) devolvem o controle ao SO para decidir.
- O processo que aguarda a rede permanece **bloqueado** até uma **interrupção de rede/chamada de sistema** completar; então vai a **pronto**.
- Trocas de CPU acontecem por **quantum** esgotado, prioridade ou bloqueio por **chamada de sistema**.

---

## 10. Questão de Reflexão — "Mais RAM = 100% de CPU?"

**Incorreta.** No modelo U = 1 − pⁿ:
- Aumentar n → pⁿ → 0 → U → 1, mas **nunca igual a 1** para p > 0.
- O limite é uma **assíntota**: aproxima-se de 100% sem alcançar.
- Na prática há ainda overhead de troca de contexto, sincronização, contenção de E/S e picos em que todos os processos bloqueiam juntos.

---

## ✅ Checklist de Revisão

- [ ] Distinguir máquina estendida × gerenciador de recursos em situações-concreto
- [ ] Explicar pseudoparalelismo, multiplexação no tempo e diferença programa/processo
- [ ] Desenhar o diagrama de estados (executando ↔ pronto; executando → bloqueado → pronto)
- [ ] Explicar `fork()` + `execve()` e o ciclo do shell (pai espera filho)
- [ ] Descrever o caminho: modo usuário → syscall → trap → modo núcleo → retorno
- [ ] Argumentar 2+ problemas de instruções privilegiadas livres (isolamento, segurança, concorrência)
- [ ] Aplicar U = 1 − pⁿ (atenção ao grau de multiprogramação = (RAM − SO)/processo)
- [ ] Classificar algoritmos quanto à preempção e justificar o papel do timer
- [ ] Montar diagramas de Gantt (FCFS, SJF com chegadas, RR com quantum)
- [ ] Discutir quantum × overhead × tempo de resposta e o limite U → 1
