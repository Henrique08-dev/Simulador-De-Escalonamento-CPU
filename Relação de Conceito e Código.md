# Simulador de Escalonamento de Processos

## 1. Visao Geral do Projeto

Este projeto implementa um **simulador de abstracoes de recursos de Sistema Operacional**, focado no escalonamento de processos na CPU. O objetivo e demonstrar, de forma didatica e controlada, como diferentes algoritmos de escalonamento (FCFS, SJF e Round Robin) gerenciam a fila de processos, a preempcao, o decremento de instrucoes e a chegada estocastica de novos processos.

O simulador e composto por entidades que se comunicam de forma coordenada:
- **Processo**: unidade de trabalho a ser executada.
- **GeradorDeProcessos**: fabrica de processos com IDs unicos e instrucoes aleatorias.
- **Escalonador**: entidade **passiva** que decide qual processo executa a seguir.
- **CPU**: entidade ativa que consome processos em um laco de clock.
- **SimuladorController**: orquestra o inicio e o fim da simulacao.

---

## 2. Arquitetura e Fluxo de Dados

```
+-------------------+     gera      +-------------------+
| GeradorDeProcessos|-------------> |     Processo      |
+-------------------+               +-------------------+
          |                                  |
          | (25% chance a cada requisicao)   | (entra na fila)
          v                                  v
+-------------------+     requisita   +-------------------+
|       CPU         |---------------->|    Escalonador    |
|  (laco de clock)  |<----------------|  (entidade passiva)|
+-------------------+     retorna     +-------------------+
          |                                  |
          | (adiciona na fila do algoritmo)  | (fila FCFS/SJF/RR)
          v                                  v
     [Fila de Processos] <------------------+
```

**Fluxo:**
1. O `SimuladorController` cria o `GeradorDeProcessos`, o `Escalonador` e a `CPU`.
2. A `CPU` inicia seu laco infinito de clock.
3. A cada ciclo, a `CPU` **requisita** o proximo processo ao `Escalonador`.
4. O `Escalonador`, sendo passivo, so age nesse momento: verifica se chegou um novo processo (chegada estocastica), aplica o algoritmo configurado e retorna o processo escolhido.
5. A `CPU` executa **uma instrucao** do processo (decrementa em 1).
6. Se o processo terminar, a `CPU` o descarta. Se for preemptado (Round Robin), o `Escalonador` o reenfileira.
7. O laco so termina quando nao ha mais processos na fila **e** nenhum processo esta sendo executado.

---

## 3. Entidades e Conceitos Fundamentais

### 3.1. Processo

**Conceito teorico:**
Um processo e a representacao de um programa em execucao. No contexto do simulador, simplificamos o processo como uma unidade de trabalho que possui um identificador unico (PID) e uma quantidade finita de instrucoes a serem processadas pela CPU. A cada ciclo de clock em que o processo ocupa a CPU, ele consome uma instrucao. Quando todas as instrucoes sao consumidas, o processo esta finalizado.

**Implementacao no codigo (`model/Processo.java`):**

```java
public class Processo {
    private final int id;
    private int quantidadeInstrucoes;
    private final int instrucoesTotais;
    private final List<Integer> historicoExecucao;
```

- `id`: identificador unico, imutavel (`final`), gerado pelo `GeradorDeProcessos`.
- `quantidadeInstrucoes`: contador mutavel que representa quantas instrucoes ainda faltam. E decrementado a cada interacao com a CPU.
- `instrucoesTotais`: valor original, fixo, usado apenas para exibicao no `toString()`.
- `historicoExecucao`: `List<Integer>` que armazena o numero de cada ciclo de clock em que uma instrucao foi executada. Isso permite rastrear exatamente **quando** cada instrucao foi processada, facilitando a verificacao didatica do funcionamento do algoritmo.

**Metodo de execucao:**

```java
public void registrarExecucao(int cicloClock) {
    if (this.quantidadeInstrucoes > 0) {
        this.historicoExecucao.add(cicloClock);
        this.quantidadeInstrucoes--;
    }
}
```

O metodo `registrarExecucao(int cicloClock)` e chamado **exatamente uma vez por ciclo** pela CPU. Ele:
1. Registra o numero do ciclo atual no historico.
2. Decrementa a quantidade de instrucoes restantes em 1.

Isso traduz diretamente a regra do projeto: *"A quantidade de instrucoes sera decrescida em 1 a cada interacao do processo com a CPU. Ao final, o processo estara finalizado."*

**Finalizacao:**

```java
public boolean isFinalizado() {
    return this.quantidadeInstrucoes == 0;
}
```

Quando `quantidadeInstrucoes` atinge 0, o processo e considerado finalizado. A CPU entao invoca `imprimirHistorico()`, exibindo a sequencia completa de ciclos em que cada instrucao foi executada.

---

### 3.2. GeradorDeProcessos

**Conceito teorico:**
Em um sistema operacional real, processos sao criados pelo usuario, pelo sistema ou por outros processos (fork). No simulador, precisamos de uma entidade capaz de fabricar processos de forma automatica, atribuindo a cada um um identificador unico e uma carga de trabalho (quantidade de instrucoes) variavel.

**Implementacao no codigo (`service/GeradorDeProcessos.java`):**

```java
public class GeradorDeProcessos {
    private final AtomicInteger contadorId = new AtomicInteger(1);
    private final Random random = new Random();

    public Processo gerarProcesso() {
        int id = contadorId.getAndIncrement();
        int instrucoes = random.nextInt(41) + 10; // 10 a 50
        return new Processo(id, instrucoes);
    }
}
```

- `AtomicInteger contadorId`: garante que cada processo receba um ID **unico e incremental** (1, 2, 3...). O uso de `AtomicInteger` e uma escolha de thread-safety: mesmo que, em extensoes futuras, multiplas threads chamem `gerarProcesso()` simultaneamente, nunca havera IDs duplicados.
- `Random random`: gerador de numeros aleatorios.
- `random.nextInt(41) + 10`: gera um valor entre **10 e 50** (inclusive), conforme especificado no projeto. Isso simula processos com cargas de trabalho variadas.

A entidade nao decide **quando** colocar o processo na fila; ela apenas o **fabrica**. Quem decide inseri-lo no sistema e o `Escalonador`, atraves da chegada estocastica.

---

### 3.3. Escalonador (Entidade Passiva)

**Conceito teorico:**
O escalonador (scheduler) e o componente do sistema operacional responsavel por decidir **qual processo na fila de prontos (ready queue) sera executado pela CPU a seguir**. No nosso simulador, ele foi especificado como uma **entidade passiva**, o que significa que ele nao possui uma thread propria nem toma iniciativa. Ele apenas **reage** quando a CPU o solicita.

**Implementacao no codigo (`service/Escalonador.java`):**

```java
public class Escalonador {
    private final TipoAlgoritmo algoritmo;
    private final int quantum;
    private int quantumAtual;
    private final GeradorDeProcessos gerador;
    private final Random random = new Random();
```

O escalonador mantem:
- O `algoritmo` ativo (FCFS, SJF ou Round Robin).
- O `quantum` e o contador `quantumAtual` (relevantes apenas para Round Robin).
- Uma referencia ao `GeradorDeProcessos` para criar novos processos sob demanda.
- Tres filas distintas, uma para cada algoritmo.

**Por que tres filas?**
O escalonador instancia as tres estruturas, mas utiliza **apenas a fila correspondente ao algoritmo selecionado** no momento da execucao. Isso permite que a escolha do algoritmo seja feita por parametro no construtor, sem necessidade de recompilacao ou alteracao de codigo.

**Metodo principal:**

```java
public synchronized Processo obterProximoProcesso(Processo processoAtual) {
    if (random.nextDouble() < 0.25) {
        Processo novo = gerador.gerarProcesso();
        adicionarProcesso(novo);
    }
    // ... delega para o algoritmo ativo
}
```

Este metodo e o coracao da entidade passiva. Ele so e invocado quando a CPU o chama. A palavra-chave `synchronized` garante que, mesmo em um cenario multithread, o acesso as filas seja seguro e consistente.

---

### 3.4. CPU

**Conceito teorico:**
A Unidade Central de Processamento (CPU) e o recurso computacional que executa as instrucoes dos processos. No simulador, a CPU e representada por um laco infinito onde cada iteracao simula um **ciclo de clock**. A cada ciclo, a CPU pode executar uma instrucao de um unico processo.

**Implementacao no codigo (`service/CPU.java`):**

```java
public class CPU implements Runnable {
    private final Escalonador escalonador;
    private final int msPorCiclo;

    @Override
    public void run() {
        Processo processoAtual = null;
        long cicloClock = 1;

        while (escalonador.possuiProcessos() || processoAtual != null) {
            System.out.printf("\n[CICLO %d]\n", cicloClock);

            processoAtual = escalonador.obterProximoProcesso(processoAtual);

            if (processoAtual != null) {
                processoAtual.registrarExecucao((int) cicloClock);
                // ... exibicao e verificacao de finalizacao
            } else {
                System.out.println("  CPU em estado ocioso, aguardando novos processos.");
            }

            cicloClock++;
            Thread.sleep(msPorCiclo);
        }
    }
}
```

**Laco de execucao:**
O laco `while (escalonador.possuiProcessos() || processoAtual != null)` traduz a condicao de vida da CPU:
- `escalonador.possuiProcessos()`: verifica se ha processos aguardando em alguma fila.
- `processoAtual != null`: verifica se a CPU ainda esta executando um processo (que pode ter sido preemptado ou estar em seu ultimo ciclo).

**A CPU so desliga quando ambas as condicoes sao falsas**, ou seja, quando nao ha mais nada para fazer. Isso garante que todos os processos sejam processados ate o fim.

**Requisicao ao Escalonador:**
A cada ciclo, a CPU chama `escalonador.obterProximoProcesso(processoAtual)`, passando o processo que esta atualmente em execucao. O escalonador entao decide:
- Se o processo atual deve continuar (nao-preemptivo ou ainda dentro do quantum).
- Se o processo atual deve ser trocado (preempcao ou finalizacao).
- Qual o proximo processo da fila deve assumir a CPU.

**Sleep:**
O `Thread.sleep(msPorCiclo)` simula o tempo real entre ciclos de clock, permitindo acompanhar a execucao no console de forma legivel.

---

### 3.5. SimuladorController

**Conceito teorico:**
O controlador e o ponto de entrada e orquestracao da simulacao. Ele e responsavel por criar as entidades, injetar as dependencias, gerar a carga inicial de processos e iniciar a thread da CPU.

**Implementacao no codigo (`controller/SimuladorController.java`):**

```java
public void iniciarSimulacao(TipoAlgoritmo algoritmo, int quantum, 
                             int tempoClockMs, int cargaInicial) {

    GeradorDeProcessos gerador = new GeradorDeProcessos();
    Escalonador escalonador = new Escalonador(algoritmo, quantum, gerador);

    for (int i = 0; i < cargaInicial; i++) {
        escalonador.adicionarProcesso(gerador.gerarProcesso());
    }

    CPU cpu = new CPU(escalonador, tempoClockMs);
    Thread threadCpu = new Thread(cpu);
    threadCpu.start();

    threadCpu.join(); // Aguarda o termino natural da CPU
}
```

**Injecao de dependencias:**
O `GeradorDeProcessos` e criado e passado ao `Escalonador` no construtor. O `Escalonador` e passado a `CPU`. Isso desacopla as entidades e permite que o `Escalonador` solicite novos processos ao `Gerador` sem conhecer a `CPU`.

**Carga inicial:**
O parametro `cargaInicial` define quantos processos serao criados **antes** do inicio da simulacao, garantindo que a CPU tenha trabalho imediato.

**Aguardando o termino:**
O metodo `threadCpu.join()` faz com que a thread principal (do `main`) espere a thread da CPU encerrar naturalmente. Como a CPU para quando nao ha mais processos, o controlador so finaliza quando a simulacao realmente acaba.

---

## 4. Chegada Estocastica de Processos

**Conceito teorico:**
Em sistemas operacionais reais, processos nao chegam em horarios predefinidos ou previsiveis. Um usuario pode abrir um navegador agora, um backup automatico pode disparar em 30 segundos, e um servidor pode receber uma requisicao a qualquer momento. Esse comportamento e **estocastico** (aleatorio, governado por probabilidade). O escalonador deve estar preparado para receber novos processos a qualquer momento, mesmo enquanto outros estao sendo executados.

**Implementacao no codigo:**

```java
public synchronized Processo obterProximoProcesso(Processo processoAtual) {
    if (random.nextDouble() < 0.25) {
        Processo novo = gerador.gerarProcesso();
        adicionarProcesso(novo);
    }
    // ... logica do algoritmo
}
```

A chegada estocastica e implementada **dentro do metodo `obterProximoProcesso`**, ou seja, ela so e verificada quando a CPU requisita um processo. Isso mantem o carater **passivo** do escalonador.

- `random.nextDouble() < 0.25`: a cada requisicao da CPU, ha uma probabilidade de **25%** (chance pequena, conforme especificado) de um novo processo ser gerado e imediatamente enfileirado.
- Se a condicao for atendida, o `GeradorDeProcessos` cria um novo `Processo` com ID unico e 10 a 50 instrucoes, e o metodo `adicionarProcesso()` o insere na fila ativa.
- O processo gerado **nao assume a CPU imediatamente**; ele entra no final da fila e aguarda sua vez, conforme o algoritmo.

**Paralelo com a realidade:**
Isso simula um ambiente onde novos trabalhos chegam ao sistema de forma imprevisivel enquanto a CPU ja esta ocupada. A fila nunca esta garantidamente vazia, e o algoritmo de escalonamento deve administrar corretamente a concorrencia entre processos antigos e recem-chegados.

---

## 5. Algoritmos de Escalonamento

O escalonador implementa tres algoritmos classicos. A escolha de cada um e feita via o enum `TipoAlgoritmo`, passado como parametro no construtor do `Escalonador`. Abaixo, analisamos cada algoritmo, seu conceito teorico, a estrutura de dados utilizada e o codigo correspondente.

---

### 5.1. FCFS (First-Come, First-Served)

**Conceito teorico:**
O FCFS e o algoritmo de escalonamento mais simples. Os processos sao atendidos **na ordem exata em que chegam**. Quem chega primeiro, e executado primeiro. E um algoritmo **nao-preemptivo**: uma vez que a CPU e atribuida a um processo, ele a mantem ate terminar todas as suas instrucoes.

**Estrutura de dados:**

```java
private final Queue<Processo> filaFCFS = new LinkedList<>();
```

**Por que `LinkedList` implementando `Queue`?**
O FCFS exige uma estrutura **FIFO** (First-In, First-Out). A interface `Queue` em Java modela exatamente esse comportamento. A classe `LinkedList` e a implementacao padrao de `Queue` e oferece:
- `add(elemento)`: insere no final da fila (tail) em **O(1)**.
- `poll()`: remove e retorna o elemento do inicio da fila (head) em **O(1)**.

Essa eficiencia constante e ideal para o simulador, pois a insercao e remocao de processos sao operacoes frequentes.

**Implementacao no codigo:**

```java
private Processo escalonarFCFS(Processo atual) {
    if (atual != null && !atual.isFinalizado()) return atual;
    return filaFCFS.poll();
}
```

**Fluxo de execucao:**
1. A CPU chama `obterProximoProcesso`, passando o processo que esta rodando no momento (`atual`).
2. O metodo `escalonarFCFS` verifica: se `atual` existe e ainda nao esta finalizado, ele **continua executando** (nao-preemptivo).
3. Se `atual` for nulo (CPU ociosa) ou ja tiver terminado, o metodo retira o proximo processo do inicio da fila com `poll()`.
4. O processo retornado assumira a CPU e so a liberara quando `isFinalizado()` for verdadeiro.

**Caracteristicas observaveis:**
- Nao ha preempcao. Um processo longo pode monopolizar a CPU, fazendo processos curtos que chegaram depois esperarem muito (efeito comboio / convoy effect).
- A ordem de saida e exatamente a ordem de chegada.

---

### 5.2. SJF (Shortest Job First)

**Conceito teorico:**
O SJF prioriza o processo que possui o **menor tempo de execucao estimado** (no nosso caso, a menor quantidade de instrucoes restantes). A ideia e minimizar o tempo medio de espera, dando prioridade a processos curtos. O SJF implementado aqui e **nao-preemptivo** (tambem conhecido como Shortest Job Next): uma vez que um processo comeca, ele nao e interrompido.

**Estrutura de dados:**

```java
private final PriorityQueue<Processo> filaSJF = new PriorityQueue<>(
    Comparator.comparingInt(Processo::getQuantidadeInstrucoes)
);
```

**Por que `PriorityQueue`?**
O SJF exige que, a cada nova escolha, o processo com a **menor quantidade de instrucoes** seja selecionado. A `PriorityQueue` em Java e implementada como um **Heap binario** (min-heap, neste caso, devido ao comparator), o que garante:
- O elemento de maior prioridade (menor valor) esta sempre no topo.
- `add(elemento)`: insercao em **O(log n)**.
- `poll()`: remocao do elemento de maior prioridade em **O(log n)**.
- `peek()`: consulta do topo em **O(1)**.

O `Comparator.comparingInt(Processo::getQuantidadeInstrucoes)` define a prioridade: quanto **menor** o numero de instrucoes, **maior** a prioridade.

**Implementacao no codigo:**

```java
private Processo escalonarSJF(Processo atual) {
    if (atual != null && !atual.isFinalizado()) return atual;
    return filaSJF.poll();
}
```

**Fluxo de execucao:**
1. A CPU requisita o proximo processo.
2. Se houver um processo `atual` em execucao e nao finalizado, ele **continua** (nao-preemptivo).
3. Se a CPU estiver livre, `poll()` remove o processo com a **menor** `quantidadeInstrucoes` do topo do heap.
4. Quando novos processos chegam (via chegada estocastica), sao inseridos no heap com `add()`. O heap se reorganiza automaticamente para manter o de menor instrucao no topo.

**Caracteristicas observaveis:**
- Processos curtos tendem a ser atendidos rapidamente.
- Processos longos podem sofrer starvation (inanicao) se processos curtos chegarem constantemente antes que eles terminem.
- A ordem de execucao **nao** e a ordem de chegada; e a ordem crescente de tamanho.

---

### 5.3. Round Robin (RR)

**Conceito teorico:**
O Round Robin e projetado para sistemas **time-sharing** (compartilhamento de tempo). Cada processo recebe uma fatia de tempo fixa chamada **quantum**. Se o processo nao terminar dentro do quantum, ele e **preemptado** (forcado a liberar a CPU) e colocado no **final** da fila, dando vez ao proximo processo. Isso garante que todos os processos recebam atencao da CPU de forma ciclica e justa.

**Estrutura de dados:**

```java
private final Queue<Processo> filaRoundRobin = new LinkedList<>();
```

**Por que `LinkedList` implementando `Queue`?**
O Round Robin funciona como uma **fila circular** (circular queue). A `LinkedList` e ideal porque:
- `add(elemento)`: insere no final (tail) em **O(1)**. Usado quando um processo e preemptado e precisa "voltar para o final da fila".
- `poll()`: remove do inicio (head) em **O(1)**. Usado para pegar o proximo processo a executar.

A fila e circular no sentido logico: o processo sai da frente, usa a CPU por um quantum, e se nao terminar, volta para o final, formando um ciclo continuo.

**Implementacao no codigo:**

```java
private Processo escalonarRoundRobin(Processo atual) {
    if (atual != null) {
        if (atual.isFinalizado()) {
            quantumAtual = 0;
        } else if (quantumAtual >= quantum) {
            System.out.printf("  [!] PREEMPCAO: Quantum (%d) expirado para PID %d. Reenfileirando.\n", 
                quantum, atual.getId());
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

**Fluxo de execucao detalhado:**

1. **Verificacao do processo atual:**
   - Se `atual` e `null` (CPU ociosa), pula direto para pegar o proximo da fila.
   - Se `atual.isFinalizado()`: o processo terminou. O contador `quantumAtual` e zerado. A CPU deve buscar um novo processo.
   - Se `quantumAtual >= quantum`: o processo usou toda sua fatia de tempo. Ocorre a **PREEMPCAO**:
     - O processo e adicionado ao **final** da fila (`filaRoundRobin.add(atual)`).
     - `quantumAtual` e zerado.
     - A CPU buscara o proximo processo.
   - Se nenhuma das condicoes acima: o processo ainda tem tempo de quantum sobrando. `quantumAtual` e incrementado e o mesmo processo continua na CPU.

2. **Selecao do proximo processo:**
   - `filaRoundRobin.poll()` retira o processo do inicio da fila.
   - Se existir um proximo processo, `quantumAtual` e inicializado em **1** (pois o ciclo atual em que ele foi pego ja conta como o primeiro ciclo de execucao dele).

**Por que `quantumAtual = 1` quando pega um novo processo?**
Quando o metodo retorna o novo processo, a CPU imediatamente executa uma instrucao dele naquele mesmo ciclo de clock. Portanto, ele ja consumiu 1 unidade de quantum. Inicializar em 1 garante que, no proximo ciclo, se o quantum for 2, o processo execute no ciclo atual (1) e no seguinte (2), sendo preemptado no ciclo seguinte ao termino do quantum.

**Caracteristicas observaveis:**
- Todos os processos recebem a CPU de forma rotativa.
- Nenhum processo monopoliza a CPU por muito tempo (a nao ser que seja o unico na fila).
- O tempo de resposta e previsivel e justo para processos interativos.
- O desempenho depende fortemente do tamanho do quantum: quantum muito pequeno gera muita sobrecarga de troca de contexto; quantum muito grande aproxima o RR do FCFS.

---

## 6. Estruturas de Dados: Resumo Comparativo

| Algoritmo | Estrutura | Classe Java | Complexidade Insercao | Complexidade Remocao | Justificativa |
|---|---|---|---|---|---|
| **FCFS** | Fila FIFO | `LinkedList<Processo>` | O(1) | O(1) | Ordem de chegada e a ordem de execucao. |
| **SJF** | Heap (Fila de Prioridade) | `PriorityQueue<Processo>` | O(log n) | O(log n) | Sempre precisa do processo com **menor** numero de instrucoes. |
| **Round Robin** | Fila Circular | `LinkedList<Processo>` | O(1) | O(1) | Processos preemptados voltam para o **final**; proximo vem da **frente**. |

---

## 7. Preempcao vs. Nao-Preempcao no Projeto

**Conceito:**
- **Nao-preemptivo**: uma vez que a CPU e dada a um processo, so o proprio processo (ao terminar) pode libera-la.
- **Preemptivo**: o sistema operacional (ou o escalonador, neste caso) pode "tirar" a CPU do processo a qualquer momento, baseado em uma regra (ex: fim do quantum).

**Aplicacao no codigo:**

| Algoritmo | Tipo | Ponto de decisao no codigo |
|---|---|---|
| **FCFS** | Nao-preemptivo | `if (atual != null && !atual.isFinalizado()) return atual;` |
| **SJF** | Nao-preemptivo | `if (atual != null && !atual.isFinalizado()) return atual;` |
| **Round Robin** | Preemptivo | `if (quantumAtual >= quantum) { filaRoundRobin.add(atual); }` |

Note que FCFS e SJF compartilham a mesma logica de continuacao: se ha um processo atual e ele nao acabou, a CPU nao o troca. No Round Robin, essa regra e substituida pela verificacao do quantum.

---

## 8. Decremento de Instrucoes e Ciclo de Clock

**Conceito:**
Um ciclo de clock representa a unidade basica de tempo do processador. No simulador, simplificamos para que **1 ciclo de clock = 1 instrucao executada**.

**Implementacao:**
Na `CPU`:
```java
processoAtual.registrarExecucao((int) cicloClock);
```

No `Processo`:
```java
public void registrarExecucao(int cicloClock) {
    if (this.quantidadeInstrucoes > 0) {
        this.historicoExecucao.add(cicloClock);
        this.quantidadeInstrucoes--;
    }
}
```

A cada iteracao do laco `while` na CPU, um unico processo (ou nenhum, se ociosa) tem sua instrucao decrementada. O numero do ciclo e guardado no historico, permitindo rastrear exatamente em que momento do tempo de simulacao cada instrucao foi processada.

---

## 9. Historico de Execucao

**Conceito:**
Para fins didaticos, cada processo mantem um registro completo de sua passagem pela CPU. Isso permite verificar se a preempcao ocorreu nos ciclos corretos e se a ordem de execucao das instrucoes esta coerente com o algoritmo.

**Implementacao:**
```java
private final List<Integer> historicoExecucao; // guarda o numero do ciclo

public void imprimirHistorico() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < historicoExecucao.size(); i++) {
        sb.append(String.format("Instrucao %d: Ciclo %d", i + 1, historicoExecucao.get(i)));
        if (i < historicoExecucao.size() - 1) sb.append(", ");
    }
    System.out.printf("  [HISTORICO PID %d] %s\n", id, sb.toString());
}
```

Exemplo de saida:
```
  [HISTORICO PID 3] Instrucao 1: Ciclo 7, Instrucao 2: Ciclo 8, Instrucao 3: Ciclo 15
```

Isso mostra que o PID 3 teve suas 3 instrucoes executadas nos ciclos 7, 8 e 15. A lacuna entre o ciclo 8 e o 15 indica que o processo sofreu preempcao (no caso do Round Robin) e so voltou a CPU ciclos depois.

---

## 10. Threading e Sincronizacao

**Conceito:**
A CPU roda em uma thread separada para simular a independencia do hardware em relacao ao controlador. O escalonador, por ser passivo, nao possui thread propria; ele e chamado sincronamente pela thread da CPU.

**Implementacao:**
```java
Thread threadCpu = new Thread(cpu);
threadCpu.start();
threadCpu.join();
```

O metodo `obterProximoProcesso` e `adicionarProcesso` sao marcados como `synchronized`:
```java
public synchronized void adicionarProcesso(Processo p) { ... }
public synchronized Processo obterProximoProcesso(Processo processoAtual) { ... }
```

Isso garante que, se futuras extensoes do projeto incluirem multiplas fontes de geracao de processos (ex: multiplas threads de E/S), o acesso as filas internas do escalonador sera seguro e livre de condicoes de corrida (race conditions).

---

## 11. Como Executar

### Requisitos
- Java JDK 8 ou superior.
- Terminal com suporte a UTF-8 (para exibicao dos caracteres do console).

### Compilacao
A partir da raiz do projeto (onde esta a pasta `src` ou os pacotes `model`, `service`, `controller`):

```bash
javac -d out model/*.java service/*.java controller/*.java Main.java
```

### Execucao
```bash
java -cp out Main
```

### Configuracao
Edite a classe `Main.java` para alterar os parametros da simulacao:

```java
controller.iniciarSimulacao(
    TipoAlgoritmo.ROUND_ROBIN,  // Algoritmo: FCFS, SJF ou ROUND_ROBIN
    2,                          // Quantum (usado apenas no Round Robin)
    1000,                       // Milissegundos por ciclo de clock
    2                           // Quantidade de processos iniciais
);
```

---

## 12. Exemplo de Saida Esperada (Round Robin)

```
========================================
       PREPARANDO AMBIENTE
========================================
  [+] NOVO PROCESSO NA FILA: [PID: 1 | Instrucoes: 14/14]
  [+] NOVO PROCESSO NA FILA: [PID: 2 | Instrucoes: 10/10]

========================================
     INICIANDO SIMULACAO CONTROLADA
========================================

[CICLO 1]
  CPU executando PID 1 | Restam: 13 instrucoes

[CICLO 2]
  CPU executando PID 1 | Restam: 12 instrucoes

[CICLO 3]
  [!] PREEMPCAO: Quantum (2) expirado para PID 1. Reenfileirando.
  CPU executando PID 2 | Restam: 9 instrucoes

[CICLO 4]
  CPU executando PID 2 | Restam: 8 instrucoes

[CICLO 5]
  [!] PREEMPCAO: Quantum (2) expirado para PID 2. Reenfileirando.
  CPU executando PID 1 | Restam: 11 instrucoes
...
  [X] PID 2 FINALIZADO!
  [HISTORICO PID 2] Instrucao 1: Ciclo 3, Instrucao 2: Ciclo 4, ...
```

---

## 13. Conclusao

Este simulador traduz conceitos fundamentais de Sistemas Operacionais — processos, filas, preempcao, quantum e chegada estocastica — em um codigo Java estruturado e didatico. Cada escolha de estrutura de dados (`LinkedList` para FIFO, `PriorityQueue` para SJF) e cada decisao de design (escalonador passivo, laco de clock na CPU, decremento unitario de instrucoes) reflete diretamente um principio teorico da computacao, permitindo que o comportamento do sistema operacional seja observado e verificado passo a passo no console.
