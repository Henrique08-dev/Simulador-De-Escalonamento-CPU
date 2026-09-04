## Visão Geral da Simulação

A simulação é composta por quatro entidades principais:

- **Processo** – representa uma tarefa com um número de instruções a serem executadas.
- **GeradorDeProcessos** – cria novos processos periodicamente (de forma probabilística) com ID único e burst aleatório.
- **Escalonador** – mantém a fila de prontos e, sob demanda da CPU, seleciona o próximo processo a ser executado conforme o algoritmo configurado.
- **CPU** – executa um ciclo de clock, pedindo um processo ao escalonador, executando uma instrução e dormindo por um intervalo configurável.

O controlador (`SimuladorController`) orquestra a criação dos componentes e inicia a thread da CPU. A simulação termina quando a fila de prontos se esvazia e não há mais processos em execução.

---

## Estrutura do Projeto

```
src/
├── controller/
│   └── SimuladorController.java
├── model/
│   ├── Processo.java
│   └── TipoAlgoritmo.java
├── service/
│   ├── CPU.java
│   ├── Escalonador.java
│   └── GeradorDeProcessos.java
└── Main.java
```

---

## Detalhamento das Classes

### 1. `Processo` (model)

Atributos:
- `id` – identificador único.
- `quantidadeInstrucoes` – contador de instruções restantes (decrementado a cada execução).
- `instrucoesTotais` – valor inicial (usado para estatísticas).
- `historicoExecucao` – lista dos ciclos de clock em que cada instrução foi executada.

Métodos principais:
- `registrarExecucao(int cicloClock)` – decrementa o contador e armazena o ciclo.
- `isFinalizado()` – retorna `true` se `quantidadeInstrucoes == 0`.
- `imprimirHistorico()` – exibe em qual ciclo cada instrução foi executada, útil para depuração.

**Paralelo com a teoria:** cada processo possui um *burst time* (tempo de execução) representado pelo número de instruções. O histórico permite rastrear o escalonamento ao longo do tempo.

---

### 2. `GeradorDeProcessos` (service)

Utiliza um contador atômico para IDs incrementais e um gerador aleatório. O método `gerarProcesso()` cria um novo processo com quantidade de instruções entre **10 e 50** (inclusive), conforme especificação.

**Paralelo com a teoria:** em sistemas reais, novos processos chegam a intervalos aleatórios. Aqui, a geração é controlada probabilisticamente, simulando a natureza estocástica das cargas de trabalho.

---

### 3. `Escalonador` (service)

É o coração da simulação. Mantém três filas distintas, uma para cada algoritmo, e implementa a lógica de seleção. Recebe no construtor o tipo de algoritmo e o *quantum* (usado apenas no Round Robin).

#### Filas:
- `filaFCFS` – `LinkedList` (FIFO).
- `filaSJF` – `PriorityQueue` ordenada pelo número de instruções restantes (menor primeiro).
- `filaRoundRobin` – `LinkedList` (FIFO para reenfileiramento).

#### Métodos principais:

- `adicionarProcesso(Processo p)` – insere o processo na fila correspondente ao algoritmo ativo.
- `obterProximoProcesso(Processo processoAtual)` – é chamado a cada ciclo pela CPU. Antes de selecionar, há **25% de chance** de gerar um novo processo (simulando chegada assíncrona). Em seguida, delega para o método específico do algoritmo.
- `escalonarFCFS`, `escalonarSJF`, `escalonarRoundRobin` – implementam a lógica de cada política.
- `possuiProcessos()` – verifica se há processos em qualquer fila, usado para decidir quando a simulação deve terminar.

**Paralelo com a teoria:** o escalonador é o componente responsável por implementar a política de escalonamento. Ele decide qual processo será executado a cada ciclo, baseando‑se no estado das filas e no algoritmo configurado.

---

### 4. `CPU` (service)

Implementa `Runnable` e executa um loop enquanto houver processos na fila ou um processo atual em execução. Em cada iteração (ciclo de clock):

1. Solicita o próximo processo ao escalonador (`escalonador.obterProximoProcesso(processoAtual)`).
2. Se houver processo, registra a execução de uma instrução (`processoAtual.registrarExecucao()`).
3. Se o processo finalizou, imprime seu histórico e anula a referência.
4. Aguarda (`Thread.sleep(msPorCiclo)`) para simular o tempo de ciclo.

O loop termina quando `escalonador.possuiProcessos()` retorna `false` **e** `processoAtual == null`.

**Paralelo com a teoria:** a CPU executa as instruções de um processo por vez. O ciclo de clock é abstraído pelo `sleep`, permitindo visualizar a evolução em tempo real.

---

### 5. `SimuladorController` (controller)

Responsável por:
- Instanciar o `GeradorDeProcessos`, o `Escalonador` e a `CPU`.
- Adicionar a carga inicial de processos (parâmetro `cargaInicial`).
- Iniciar a thread da CPU e aguardar seu término (`join()`).

A simulação encerra naturalmente quando não há mais processos a executar, sem necessidade de definir uma duração fixa.

---

### 6. `Main` (ponto de entrada)

Cria uma instância do controlador e invoca `iniciarSimulacao` com os parâmetros desejados:

- `TipoAlgoritmo` – `FCFS`, `SJF` ou `ROUND_ROBIN`.
- `quantum` – valor inteiro (relevante apenas para Round Robin).
- `tempoClockMs` – intervalo em milissegundos entre ciclos (ex.: 1000 ms para visualização lenta).
- `cargaInicial` – número de processos criados antes de iniciar a CPU.

Exemplo para testar SJF:
```java
controller.iniciarSimulacao(TipoAlgoritmo.SJF, 2, 1000, 2);
```

---

## Fluxo de Execução

1. O `Main` chama o controlador com os parâmetros.
2. O controlador gera a carga inicial de processos e os insere no escalonador.
3. A thread da CPU é iniciada e entra em loop.
4. A cada ciclo:
   - O escalonador decide se gera um novo processo (probabilidade de 25%).
   - O escalonador seleciona o próximo processo conforme o algoritmo.
   - A CPU executa uma instrução desse processo.
   - Se finalizado, o processo é removido e seu histórico é exibido.
5. Quando não há processos na fila e o processo atual é nulo, o loop termina.
6. A CPU é desligada e o controlador finaliza a simulação.

---

## Algoritmos de Escalonamento – Teoria vs. Código

### FCFS (First‑Come, First‑Served)

**Teoria:** os processos são executados na ordem de chegada. É **não preemptivo** – uma vez que um processo começa, ele ocupa a CPU até terminar.

**Código:** a `filaFCFS` é uma `LinkedList`. O método `escalonarFCFS` mantém o processo atual enquanto ele não finalizar; quando finaliza, retira o próximo da fila (`poll()`).
```java
private Processo escalonarFCFS(Processo atual) {
    if (atual != null && !atual.isFinalizado()) return atual;
    return filaFCFS.poll();
}
```

---

### SJF (Shortest Job First)

**Teoria:** seleciona o processo com o menor *burst time* (tempo de execução) entre todos os prontos. A versão implementada é **não preemptiva** – o processo em execução só é trocado ao finalizar.

**Código:** a `filaSJF` é uma `PriorityQueue` que ordena os processos por `getQuantidadeInstrucoes()` (menor primeiro). O método `escalonarSJF` tem a mesma estrutura do FCFS, mas obtém o próximo da `PriorityQueue`.
```java
private Processo escalonarSJF(Processo atual) {
    if (atual != null && !atual.isFinalizado()) return atual;
    return filaSJF.poll();
}
```

---

### Round Robin

**Teoria:** cada processo recebe uma fatia de tempo (*quantum*) para executar. Se não terminar dentro do quantum, é preemptado e colocado no final da fila de prontos. É **preemptivo** por natureza.

**Código:** a `filaRoundRobin` é uma `LinkedList`. O método `escalonarRoundRobin` controla o `quantumAtual`:
- Se o processo atual finalizou, zera o quantum.
- Se o quantum foi atingido (`quantumAtual >= quantum`), o processo é reenfileirado e o quantum é zerado.
- Caso contrário, incrementa o quantum e retorna o mesmo processo.
- Se o processo atual é nulo ou foi preemptado, retira o próximo da fila e reinicia o quantum.

```java
private Processo escalonarRoundRobin(Processo atual) {
    if (atual != null) {
        if (atual.isFinalizado()) {
            quantumAtual = 0;
        } else if (quantumAtual >= quantum) {
            // preempção
            filaRoundRobin.add(atual);
            quantumAtual = 0;
        } else {
            quantumAtual++;
            return atual;
        }
    }
    Processo proximo = filaRoundRobin.poll();
    if (proximo != null) quantumAtual = 1;
    return proximo;
}
```

---

## Chegada Estocástica de Processos

A simulação incorpora a geração aleatória de novos processos durante a execução. No método `obterProximoProcesso`, há uma probabilidade de **25%** (configurável) de o escalonador solicitar um novo processo ao `GeradorDeProcessos`. Isso representa o comportamento real de sistemas operacionais, onde a carga de trabalho não é estática.

**Por que 25%?** Esse valor é arbitrário e pode ser ajustado para aumentar ou diminuir a taxa de chegada. Quanto maior a probabilidade, mais processos são gerados e a fila tende a crescer, tornando a simulação mais dinâmica.


