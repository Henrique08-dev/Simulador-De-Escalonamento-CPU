# Simulador de Alocação de Memória — Atividade 3

## Sumário

1. [Objetivo do Projeto](#1-objetivo-do-projeto)
2. [Conceitos Teóricos Abordados](#2-conceitos-teóricos-abordados)
3. [Estrutura do Projeto](#3-estrutura-do-projeto)
4. [As Entidades do Domínio (pacote `model` e `service`)](#4-as-entidades-do-domínio-pacote-model-e-service)
5. [Os 4 Algoritmos de Alocação — Teoria vs. Código](#5-os-4-algoritmos-de-alocação--teoria-vs-código)
6. [O Simulador — Conceito vs. Implementação](#6-o-simulador--conceito-vs-implementação)
7. [O Experimento — Estatística sobre a Aleatoriedade](#7-o-experimento--estatística-sobre-a-aleatoriedade)
8. [Como Compilar e Executar](#8-como-compilar-e-executar)
9. [Resultados Esperados](#9-resultados-esperados)

---

## 1. Objetivo do Projeto

Este projeto implementa um **simulador de alocação de memória contígua** que exercita, de forma controlada e reproduzível, os quatro algoritmos clássicos de alocação dinâmica de partição vistos em teoria de Sistemas Operacionais:

- **First Fit** (primeiro encaixe)
- **Next Fit** (próximo encaixe)
- **Best Fit** (melhor encaixe)
- **Worst Fit** (pior encaixe)

O programa simula a chegada e a saída de processos em uma memória de **1000 unidades** durante **100 segundos simulados**, medindo três métricas:

1. **Tamanho médio dos processos gerados**;
2. **Ocupação média da memória por segundo** (em percentual);
3. **Taxa de descarte** (processos que não conseguiram ser alocados por falta de espaço).

Para minimizar o peso da aleatoriedade, cada algoritmo é executado **100 vezes**, e as médias globais são calculadas sobre essas 100 execuções.

---

## 2. Conceitos Teóricos Abordados

| Conceito teórico | O que representa no código |
|---|---|
| **Memória principal** de tamanho fixo | Classe `Memoria`, com capacidade fixa de `1000` |
| **Partições/blocos** de memória (ocupados ou livres) | Classe `BlocoMemoria`, que sabe se está livre ou ocupado |
| **Lista encadeada de blocos** (endereços adjacentes) | `LinkedList<BlocoMemoria>` dentro de `Memoria` |
| **Processo** (ID único, tamanho de alocação) | Classe `Processo` |
| **Alocação dinâmica** (buracos de tamanho variável) | Método `Memoria.alocar(processo, algoritmo)` |
| **Fragmentação externa** e **coalescência** | Método `unirBlocosLivres()` |
| **Algoritmos First/Next/Best/Worst Fit** | Métodos privados na classe `Memoria` |
| **Relógio simulado (1 segundo = 1 iteração)** | Laço `for` no `SimuladorMemoria` |
| **Carga de trabalho aleatória** | `GeradorDeProcessos` (tamanho 10–50) + `Random` |
| **Repetição de experimento para média** | `ExperimentoAlocacao` (100 execuções) |

---

## 3. Estrutura do Projeto

```
src/
├── Main.java                        (ponto de entrada)
├── model/
│   ├── Processo.java                (entidade: processo)
│   ├── BlocoMemoria.java            (entidade: bloco da lista encadeada)
│   ├── Memoria.java                 (entidade: memória + os 4 algoritmos)
│   ├── ResultadoSimulacao.java      (DTO: métricas de 1 simulação)
│   ├── ResultadoExperimento.java    (DTO: médias de N simulações)
│   └── TipoAlgoritmoAlocacao.java   (enum com os 4 algoritmos)
├── service/
│   └── GeradorDeProcessos.java      (gera processos com ID único e tamanho 10–50)
└── controller/
    ├── SimuladorMemoria.java        (simula 1 execução de 100 segundos)
    └── ExperimentoAlocacao.java     (repete a simulação 100x e calcula médias)
```

A arquitetura segue o princípio de **separação em camadas**:

- **`model`** — contém apenas as entidades do domínio e as regras puras de alocação (a "memória física" e seus blocos). Não sabe nada sobre tempo, randomização ou experimentos.
- **`service`** — contém o gerador de carga de trabalho (a "fábrica" de processos).
- **`controller`** — orquestra a simulação no tempo (`SimuladorMemoria`) e a repetição estatística do experimento (`ExperimentoAlocacao`).
- **`Main`** — apenas escolhe o modo de execução (demonstração ou experimento completo).

---

## 4. As Entidades do Domínio (pacote `model` e `service`)

### 4.1 `Processo`

Representa um processo que precisa de memória.

```java
public class Processo {
    private final int id;
    private final int tamanho;
}
```

- **`id`**: identificador único e incremental, garantido pelo `GeradorDeProcessos` — em teoria, é o PID do processo.
- **`tamanho`**: quantidade de memória que o processo precisa ocupar (entre 10 e 50, conforme a especificação da atividade).

O `toString()` formata o processo como `[PID: X | Tamanho: Y]`, o que facilita a leitura dos logs durante a demonstração passo a passo.

### 4.2 `GeradorDeProcessos` (pacote `service`)

É a entidade responsável por criar novos processos "quando solicitada", exatamente como pedido na atividade:

```java
public class GeradorDeProcessos {
    private final AtomicInteger contadorId = new AtomicInteger(1);
    private final Random random = new Random();

    public Processo gerarProcesso() {
        int id = contadorId.getAndIncrement();
        int tamanho = random.nextInt(41) + 10;
        return new Processo(id, tamanho);
    }
}
```

**Paralelo conceito ↔ código:**

- **"ID único (incremental)"** → o `AtomicInteger` começa em 1 e cada chamada a `gerarProcesso()` retorna `getAndIncrement()`, produzindo a sequência 1, 2, 3, 4... Sem isso, dois processos poderiam receber o mesmo ID, o que quebraria a remoção por ID na memória.
- **"Tamanho de alocação aleatório entre 10 e 50"** → `random.nextInt(41)` gera valores de 0 a 40; somando 10, obtemos o intervalo **[10, 50]** com distribuição uniforme. Isso modela processos de tamanhos heterogêneos, essencial para que os algoritmos de alocação sejam realmente exercitados (se todos os processos tivessem o mesmo tamanho, qualquer algoritmo se comportaria igual).

> **Por que `AtomicInteger` em vez de um `int` comum?** Embora a simulação seja single-threaded, o `AtomicInteger` garante atomicidade do incremento e comunica a intenção: o contador é um recurso compartilhado de geração de identidade. É uma escolha defensiva e semanticamente correta.

### 4.3 `BlocoMemoria`

Cada nó da lista encadeada que representa a memória:

```java
public class BlocoMemoria {
    private int inicio;        // posição inicial dentro da memória
    private int tamanho;       // tamanho do bloco
    private Processo processo; // null = bloco LIVRE
}
```

- Um bloco **livre** tem `processo == null`.
- Um bloco **ocupado** tem `processo != null` — e o tamanho do bloco é, na prática, o tamanho do processo que o ocupa.
- O par `(inicio, tamanho)` permite verificar a **adjacência física**: dois blocos consecutivos na lista representam regiões vizinhas da memória (endereço inicial + tamanho do primeiro = endereço inicial do segundo). Essa propriedade é o que permite a **coalescência** (unir blocos livres vizinhos).

### 4.4 `Memoria` — a estrutura de dados central

```java
public class Memoria {
    private final int tamanho = 1000;
    private final LinkedList<BlocoMemoria> blocos;
    private int indiceNextFit;
}
```

No construtor, a memória nasce como **um único bloco livre de 1000**:

```java
blocos.add(new BlocoMemoria(0, tamanho));
```

Isso é exatamente a representação teórica de uma memória recém-inicializada: um único "buraco" (hole) de tamanho total, pronto para receber partições dinâmicas.

#### ⭐ Por que `LinkedList` e não `ArrayList`?

Esta é a decisão estrutural mais importante do projeto, e ela decorre **diretamente da natureza dos algoritmos de alocação**:

1. **Inserção e remoção no meio da lista são operações dominantes.** Toda alocação bem-sucedida divide um bloco livre em dois (bloco ocupado + bloco livre restante) — isso é um `add(indice + 1, novoBloco)`. Toda liberação de processo pode fundir dois blocos livres em um — isso é um `remove(i + 1)`. Na `LinkedList`, essas operações são **O(1)** (apenas ajuste de ponteiros dos nós vizinhos); num `ArrayList`, seriam **O(n)** por causa do deslocamento de todos os elementos subsequentes. Como a memória pode chegar a ter dezenas de blocos e essas operações acontecem centenas de vezes por simulação (e 100 simulações por algoritmo), a diferença de desempenho é significativa.

2. **A lista encadeada espelha o modelo teórico.** Em SO, a memória contígua é descrita como uma **lista encadeada de blocos** onde cada nó aponta para o vizinho físico. Usar `LinkedList` é a tradução literal do diagrama da teoria para código: cada `BlocoMemoria` é um nó, e a ordem na lista é a ordem dos endereços na memória.

3. **Busca sequencial é natural.** Todos os quatro algoritmos percorrem os blocos livres linearmente, do início ao fim (ou a partir de um ponto). A `LinkedList` oferece iterator eficiente e acesso por índice suficiente para esse padrão de uso.

> Em resumo: a escolha da `LinkedList` **não é acidental** — ela é a estrutura de dados cuja complexidade de operações corresponde exatamente às operações que os algoritmos de alocação exigem (fatiar e fundir blocos com frequência).

#### O mecanismo comum de alocação: `ocuparBloco`

```java
private void ocuparBloco(int indice, Processo processo) {
    BlocoMemoria bloco = blocos.get(indice);

    int tamanhoOriginal = bloco.getTamanho();
    int tamanhoProcesso = processo.getTamanho();

    bloco.setTamanho(tamanhoProcesso);
    bloco.setProcesso(processo);

    int tamanhoRestante = tamanhoOriginal - tamanhoProcesso;

    if (tamanhoRestante > 0) {
        int inicioBlocoLivre = bloco.getInicio() + tamanhoProcesso;
        BlocoMemoria blocoLivre = new BlocoMemoria(inicioBlocoLivre, tamanhoRestante);
        blocos.add(indice + 1, blocoLivre);
    }
}
```

**Paralelo teórico:** quando um processo é alocado num buraco maior que ele, o sistema operacional **divide o buraco em duas partes**: uma ocupada pelo processo e outra que permanece livre (o buraco residual). No código:

- O bloco escolhido encolhe para o tamanho exato do processo e recebe o processo (`setProcesso`);
- Um **novo bloco livre** é criado logo **após** (`indice + 1`), com início calculado pela adjacência física (`inicio + tamanhoProcesso`);
- Se o encaixe foi exato (`tamanhoRestante == 0`), não sobra buraco residual — exatamente o que a teoria prevê.

#### Coalescência: `unirBlocosLivres`

```java
private void unirBlocosLivres() {
    for (int i = 0; i < blocos.size() - 1; i++) {
        BlocoMemoria atual = blocos.get(i);
        BlocoMemoria proximo = blocos.get(i + 1);

        if (atual.isLivre() && proximo.isLivre()) {
            atual.setTamanho(atual.getTamanho() + proximo.getTamanho());
            blocos.remove(i + 1);
            i--; // reavalia: podem existir mais livres seguidos
        }
    }
}
```

**Paralelo teórico:** quando um processo sai, ele deixa um buraco. Se o vizinho já estava livre, tem-se **fragmentação externa artificial** — dois buracos adjacentes que na prática são um só espaço contíguo. O SO resolve isso com a **coalescência** (fusão de buracos vizinhos). O código faz exatamente isso: percorre a lista, e sempre que dois blocos livres consecutivos aparecem, funde-os num só (soma os tamanhos, remove o segundo). O `i--` garante que uma cadeia de três ou mais blocos livros seguidos seja completamente fundida numa única passagem.

---

## 5. Os 4 Algoritmos de Alocação — Teoria vs. Código

Todos os quatro são implementados como métodos privados dentro de `Memoria` e expostos através do método **fábrica** `alocar(processo, algoritmo)`, que usa um `switch` sobre o enum `TipoAlgoritmoAlocacao`. Esse padrão (enum + switch) permite que o `SimuladorMemoria` trate todos os algoritmos de forma uniforme: ele apenas pede "aloque este processo com este algoritmo", sem conhecer os detalhes internos de cada estratégia — exatamente como o escalonador de memória de um SO real.

Todos retornam `boolean`: `true` se o processo coube, `false` se foi necessário **descartá-lo por falta de espaço** (requisito da atividade).

### 5.1 First Fit — "o primeiro buraco que serve"

**Teoria:** percorre a lista de buracos a partir do início e aloca no **primeiro** bloco livre com tamanho suficiente. É o mais rápido em tempo de busca e tende a deixar buracos pequenos perto do início da memória.

```java
private boolean alocarFirstFit(Processo processo) {
    for (int i = 0; i < blocos.size(); i++) {
        BlocoMemoria bloco = blocos.get(i);

        if (bloco.isLivre() && bloco.getTamanho() >= processo.getTamanho()) {
            ocuparBloco(i, processo);
            return true;   // encontrou o primeiro que serve — para imediatamente
        }
    }
    return false;          // percorreu tudo e nenhum buraco serviu
}
```

**Paralelo linha a linha:**

| Teoria | Código |
|---|---|
| "Percorre do início" | `for` de `0` até `blocos.size() - 1` |
| "Bloco livre" | `bloco.isLivre()` (`processo == null`) |
| "Tamanho suficiente" | `bloco.getTamanho() >= processo.getTamanho()` |
| "Aloca no primeiro que servir e para" | `ocuparBloco(i, processo); return true;` |
| "Se nenhum serve, alocação falha" | `return false` após o laço |

Note que o `return` dentro do laço é a tradução direta da palavra "primeiro" — o algoritmo **não avalia os demais buracos**, economizando busca.

### 5.2 Next Fit — "continua de onde parou"

**Teoria:** variação do First Fit em que a busca **não recomeça do início** a cada alocação, mas do ponto onde a última alocação foi feita. A ideia é distribuir as alocações pela memória, evitando a concentração de buracos pequenos no início (característica do First Fit).

```java
private boolean alocarNextFit(Processo processo) {
    if (blocos.isEmpty()) return false;

    indiceNextFit = indiceNextFit % blocos.size();  // mantém o índice válido
    int indiceInicial = indiceNextFit;

    do {
        BlocoMemoria bloco = blocos.get(indiceNextFit);

        if (bloco.isLivre() && bloco.getTamanho() >= processo.getTamanho()) {
            int indiceAlocado = indiceNextFit;
            ocuparBloco(indiceAlocado, processo);
            indiceNextFit = (indiceAlocado + 1) % blocos.size();  // marca a próxima partida
            return true;
        }

        indiceNextFit = (indiceNextFit + 1) % blocos.size();      // avança circularmente
    } while (indiceNextFit != indiceInicial);

    return false;
}
```

**Paralelo linha a linha:**

| Teoria | Código |
|---|---|
| "Continua de onde parou" | Campo `indiceNextFit` persistido entre chamadas (estado do algoritmo) |
| "Se chegar no fim, volta ao início" | Aritmética modular `% blocos.size()` |
| "Busca circular: dá a volta completa" | `do...while (indiceNextFit != indiceInicial)` |
| "Memória pode mudar entre alocações (fusões/divisões)" | `indiceNextFit % blocos.size()` normaliza o índice antes de usar |

Dois detalhes merecem destaque:

1. **O estado entre chamadas:** diferente do First Fit, o Next Fit precisa **lembrar** onde parou. Por isso o índice é um campo da classe `Memoria` (`indiceNextFit`), não uma variável local. Isso é a tradução direta do conceito: o "ponteiro de busca" é um estado persistente do sistema.

2. **A normalização do índice:** entre uma alocação e outra, blocos podem ser fundidos (`unirBlocosLivres`) ou divididos, mudando o tamanho da lista. O módulo `% blocos.size()` garante que o índice armazenado nunca aponte para uma posição inexistente — sem isso, ocorreria `IndexOutOfBoundsException`.

### 5.3 Best Fit — "o menor buraco que serve"

**Teoria:** percorre **todos** os buracos livres e escolhe o **menor** entre os que são grandes o suficiente. A intuição é minimizar o espaço desperdiçado dentro do buraco escolhido. O custo é a busca completa, e o efeito colateral clássico é a criação de muitos **buracos pequeníssimos** (restos de encaixes quase exatos), que raramente são reutilizados.

```java
private boolean alocarBestFit(Processo processo) {
    int melhorIndice = -1;
    int menorTamanho = Integer.MAX_VALUE;

    for (int i = 0; i < blocos.size(); i++) {
        BlocoMemoria bloco = blocos.get(i);

        if (bloco.isLivre()
                && bloco.getTamanho() >= processo.getTamanho()
                && bloco.getTamanho() < menorTamanho) {

            melhorIndice = i;
            menorTamanho = bloco.getTamanho();
        }
    }

    if (melhorIndice != -1) {
        ocuparBloco(melhorIndice, processo);
        return true;
    }
    return false;
}
```

**Paralelo linha a linha:**

| Teoria | Código |
|---|---|
| "Avalia todos os candidatos" | Laço completo, sem `return` antecipado |
| "Menor bloco que acomoda o processo" | Condição `bloco.getTamanho() < menorTamanho` |
| "Guarda o melhor candidato visto até agora" | `melhorIndice` + `menorTamanho` |
| "Só aloca depois de ver tudo" | `ocuparBloco` chamado **após** o laço |
| "Nenhum candidato" | `melhorIndice == -1` → `false` |

A variável `menorTamanho` inicializada com `Integer.MAX_VALUE` é o truque clássico para "encontrar o mínimo": qualquer bloco livre válido será menor que o máximo inteiro e, portanto, substituirá o valor inicial na primeira comparação.

### 5.4 Worst Fit — "o maior buraco disponível"

**Teoria:** escolhe o **maior** buraco livre, na expectativa de que sobre um resto ainda grande o suficiente para acomodar futuros processos. Na prática, estudos e simulações mostram que o Worst Fit tende a se sair **pior** que os demais — ele rapidamente "fatia" todos os grandes buracos, degradando o espaço livre.

```java
private boolean alocarWorstFit(Processo processo) {
    int piorIndice = -1;
    int maiorTamanho = -1;

    for (int i = 0; i < blocos.size(); i++) {
        BlocoMemoria bloco = blocos.get(i);

        if (bloco.isLivre()
                && bloco.getTamanho() >= processo.getTamanho()
                && bloco.getTamanho() > maiorTamanho) {

            piorIndice = i;
            maiorTamanho = bloco.getTamanho();
        }
    }

    if (piorIndice != -1) {
        ocuparBloco(piorIndice, processo);
        return true;
    }
    return false;
}
```

**Paralelo:** é o **simétrico exato** do Best Fit — a condição de atualização usa `>` em vez de `<`, e o maior tamanho começa em `-1`. Essa simetria é proposital: demonstra que Best e Worst Fit são o mesmo algoritmo de busca de extremo, diferindo apenas no critério de ordenação (mínimo vs. máximo).

### 5.5 Quadro comparativo dos quatro algoritmos

| Algoritmo | Critério de escolha | Complexidade de busca | Estado entre chamadas? | Comportamento típico |
|---|---|---|---|---|
| **First Fit** | Primeiro livre que serve | O(n) no pior caso | Não | Buracos pequenos se acumulam no início |
| **Next Fit** | Primeiro a partir do ponto de parada | O(n) no pior caso (circular) | **Sim** (`indiceNextFit`) | Distribui alocações; evita concentração no início |
| **Best Fit** | Menor livre que serve | **Sempre O(n)** | Não | Encaixes quase exatos; sobram micro-buracos |
| **Worst Fit** | Maior livre | **Sempre O(n)** | Não | Fragmenta os grandes buracos rapidamente |

**Ponto comum a todos:** quando nenhum bloco serve, retornam `false` — e quem decide o que fazer é o chamador (o simulador), que incrementa o contador de **descartes**. No SO real, o equivalente seria o processo ficar esperando ou ser abortado; na simulação, ele é descartado, conforme a regra da atividade.

---

## 6. O Simulador — Conceito vs. Implementação

A classe `SimuladorMemoria` (pacote `controller`) é o coração temporal do projeto: ela transforma o conceito abstrato de "processos chegando e saindo da memória ao longo do tempo" em um **laço de eventos discretos**, onde cada iteração representa 1 segundo.

### 6.1 O modelo de eventos por segundo

Requisitos da atividade → tradução no código:

| Requisito (enunciado) | Implementação |
|---|---|
| "exercita a criação e alocação em memória de 2 processos por segundo" | `gerarEAlocarProcessos()` — laço `for (int i = 0; i < 2; i++)` |
| "a cada 1 segundo também, um ou dois processos aleatórios são escolhidos para sair da memória" | `removerProcessosAleatorios()` — `random.nextInt(2) + 1` sorteia 1 ou 2 |
| "durante 100 segundos" | `for (int segundo = 1; segundo <= segundos; segundo++)` |
| "caso o algoritmo não consiga alocar por falta de espaço, o processo é descartado" | `if (alocado) {...} else { totalProcessosDescartados++; }` |

### 6.2 Geração e alocação: `gerarEAlocarProcessos`

```java
private void gerarEAlocarProcessos(boolean exibirDetalhes) {
    for (int i = 0; i < 2; i++) {
        Processo processo = gerador.gerarProcesso();

        totalProcessosGerados++;
        somaTamanhoProcessos += processo.getTamanho();

        boolean alocado = memoria.alocar(processo, algoritmo);

        if (alocado) {
            processosNaMemoria.add(processo);
        } else {
            totalProcessosDescartados++;
        }
    }
}
```

Dois detalhes de modelagem importantes:

1. **Todo processo gerado entra nas métricas, mesmo os descartados.** `totalProcessosGerados++` e `somaTamanhoProcessos += ...` acontecem **antes** da tentativa de alocação. Isso é deliberado: a métrica "tamanho médio dos processos gerados" deve refletir a carga de trabalho oferecida ao sistema, não apenas a que coube. Se só contássemos os alocados, o tamanho médio ficaria enviesado (processos grandes seriam descartados com mais frequência e sumiriam da média).

2. **A lista `processosNaMemoria` (um `ArrayList`) guarda apenas os processos alocados.** Ela existe para responder à pergunta: "quais processos estão na memória agora, para que eu possa sortear 1 ou 2 para saírem?" A escolha do `ArrayList` aqui é correta e diferente da `LinkedList` da memória — ver seção 6.4.

### 6.3 Remoção aleatória: `removerProcessosAleatorios`

```java
private void removerProcessosAleatorios(boolean exibirDetalhes) {
    if (processosNaMemoria.isEmpty()) return;

    int quantidadeRemover = random.nextInt(2) + 1;          // 1 ou 2
    quantidadeRemover = Math.min(quantidadeRemover, processosNaMemoria.size());

    for (int i = 0; i < quantidadeRemover; i++) {
        int indiceAleatorio = random.nextInt(processosNaMemoria.size());
        Processo processo = processosNaMemoria.remove(indiceAleatorio);
        memoria.removerProcesso(processo.getId());
    }
}
```

**Paralelo teórico:**

- **"um ou dois processos aleatórios são escolhidos para sair"** → `nextInt(2) + 1` gera 1 ou 2 com 50% de chance cada; `nextInt(lista.size())` escolhe o processo uniformemente.
- **`Math.min`** protege o caso de haver apenas 1 processo na memória: o enunciado pede a saída de 1 ou 2, mas não é possível remover mais do que existe. Em teoria, isso é a condição de fronteira do modelo de eventos.
- **A remoção é dupla:** sai da lista de controle (`processosNaMemoria.remove`) e da memória (`memoria.removerProcesso(id)`), onde o bloco é liberado e ocorre a coalescência. Manter as duas estruturas consistentes é essencial — por isso a remoção é feita **pelo ID**, o identificador estável que liga o processo ao seu bloco.

### 6.4 Por que `ArrayList` para `processosNaMemoria` (e `LinkedList` para os blocos)?

O projeto usa **duas listas com propósitos diferentes**, e a escolha de cada uma é um reflexo direto do padrão de acesso:

| Estrutura | Lista usada | Padrão de acesso | Justificativa |
|---|---|---|---|
| Blocos de memória (`Memoria.blocos`) | `LinkedList` | Inserções/remoções **no meio** a cada operação (dividir e fundir buracos) | O(1) para inserir/remover nós; espelha o modelo teórico de lista encadeada de blocos adjacentes |
| Processos residentes (`SimuladorMemoria.processosNaMemoria`) | `ArrayList` | Acesso aleatório por índice (`nextInt(size())`) e remoção por índice sorteado | O(1) para acessar o elemento sorteado; remoções no fim são triviais; não há inserção no meio |

Ou seja: **não existe "a melhor lista" em absoluto** — existe a lista cuja complexidade combina com a operação dominante de cada contexto. O projeto demonstra exatamente essa decisão de engenharia.

### 6.5 Coleta de métricas durante a simulação

Três acumuladores são mantidos como campos do simulador:

```java
private int totalProcessosGerados;
private int totalProcessosDescartados;
private int somaTamanhoProcessos;
private double somaPercentualOcupacao;
```

A cada segundo, **após** as chegadas e partidas daquele segundo, registra-se o estado instantâneo:

```java
somaPercentualOcupacao += memoria.getPercentualOcupacao();
```

No final, as métricas da execução são calculadas:

```java
double tamanhoMedioProcessos = (double) somaTamanhoProcessos / totalProcessosGerados;
double ocupacaoMediaMemoria  = somaPercentualOcupacao / segundos;
double taxaDescarte          = ((double) totalProcessosDescartados / totalProcessosGerados) * 100;
```

| Métrica (enunciado) | Fórmula no código | Significado teórico |
|---|---|---|
| Tamanho médio dos processos gerados | Σ tamanhos ÷ nº gerados | Carga de trabalho média oferecida ao sistema |
| Ocupação média da memória por segundo | Σ ocupação diária ÷ nº de segundos | Quão cheia a memória ficou, em média, ao longo do tempo |
| Taxa de descarte (%) | descartados ÷ gerados × 100 | Percentual da demanda que o sistema **não conseguiu atender** — a métrica de qualidade do algoritmo |

O `getPercentualOcupacao()` da `Memoria` soma apenas os blocos ocupados e divide pelo total de 1000 — a tradução direta de "espaço usado / espaço total".

O método `executar` recebe ainda um parâmetro `exibirDetalhes`: quando `true`, imprime cada segundo, cada processo gerado/alocado/descartado/removido e o estado completo da memória (modo demonstração); quando `false`, executa em silêncio (modo experimento), pois imprimir 100 execuções × 100 segundos poluiria a saída sem agregar informação.

---

## 7. O Experimento — Estatística sobre a Aleatoriedade

A atividade pede explicitamente: **"repita o processo 100x, para minimizar o peso da aleatoriedade"**. A classe `ExperimentoAlocacao` implementa exatamente isso.

### 7.1 Por que repetir 100 vezes?

Como os tamanhos dos processos (10–50) e as saídas (1–2 processos/segundo) são aleatórios, **uma única simulação é uma amostra de tamanho 1** — seu resultado pode ser muito bom ou muito ruim por puro acaso. A Teoria Estatística resolve isso com o **Teorema Central do Limite**: a média de muitas amostras independentes converge para o valor esperado. Executar 100 simulações independentes e tirar a média transforma um resultado ruidoso em uma estimativa estável e confiável da performance real de cada algoritmo.

### 7.2 Implementação

```java
public ResultadoExperimento executar(
        TipoAlgoritmoAlocacao algoritmo,
        int quantidadeExecucoes,
        int segundosPorExecucao) {

    double somaTamanhoMedio = 0;
    double somaOcupacaoMedia = 0;
    double somaTaxaDescarte = 0;

    for (int i = 0; i < quantidadeExecucoes; i++) {
        // Cada execução começa com uma nova memória e novas métricas
        SimuladorMemoria simulador = new SimuladorMemoria(algoritmo);
        ResultadoSimulacao resultado = simulador.executar(segundosPorExecucao, false);

        somaTamanhoMedio += resultado.getTamanhoMedioProcessos();
        somaOcupacaoMedia += resultado.getOcupacaoMediaMemoria();
        somaTaxaDescarte += resultado.getTaxaDescarte();
    }

    double tamanhoMedioGlobal = somaTamanhoMedio / quantidadeExecucoes;
    double ocupacaoMediaGlobal = somaOcupacaoMedia / quantidadeExecucoes;
    double taxaDescarteGlobal = somaTaxaDescarte / quantidadeExecucoes;

    return new ResultadoExperimento(algoritmo, quantidadeExecucoes,
            tamanhoMedioGlobal, ocupacaoMediaGlobal, taxaDescarteGlobal);
}
```

**Paralelo conceito ↔ código:**

| Conceito estatístico | Código |
|---|---|
| "Amostra independente" | `new SimuladorMemoria(algoritmo)` **dentro** do laço — memória nova, gerador novo, contadores zerados. Se o simulador fosse reutilizado, as execuções seriam dependentes (o estado final de uma afetaria a próxima) e a média seria invalidada. |
| "Somar os valores de cada amostra" | `somaX += resultado.getX()` |
| "Média global" | `somaX / quantidadeExecucoes` |
| "Relatório por algoritmo" | `ResultadoExperimento` agrega algoritmo + nº de execuções + as 3 médias |

Note que a estrutura de agregação **espelha o enunciado**: "De cada execução, você deve extrair as seguintes métricas, e ao final calcular a média global" — primeiro extrair (`resultado.get...()`), depois acumular, e só no final dividir.

### 7.3 Os DTOs de resultado

- **`ResultadoSimulacao`** — encapsula as 3 métricas de **uma** execução (métricas locais).
- **`ResultadoExperimento`** — encapsula as 3 médias de **100** execuções (métricas globais), mais o algoritmo e a quantidade de execuções.

Separar esses dois tipos impede erros semânticos: não faz sentido somar `ResultadoExperimento`, nem dividir `ResultadoSimulacao` por 100. O compilador força o uso correto de cada nível de agregação.

---

## 8. Como Compilar e Executar

### 8.1 Compilação

Com todos os arquivos no diretório `src/`:

```bash
javac -d out -encoding UTF-8 src/Main.java src/model/*.java src/service/*.java src/controller/*.java
```

### 8.2 Execução

```bash
java -cp out Main
```

### 8.3 Modos de execução (em `Main.java`)

A variável `modoDemonstracao` seleciona o comportamento:

```java
boolean modoDemonstracao = false;
```

- **`true` — Modo Demonstração:** executa **1 simulação de 10 segundos** com `exibirDetalhes = true`, imprimindo segundo a segundo os processos gerados, alocados, descartados e removidos, além do estado completo da memória (lista de blocos). Ideal para validar o comportamento dos algoritmos e para demonstrar o funcionamento.

- **`false` — Modo Experimento:** executa o experimento completo — os 4 algoritmos × 100 execuções × 100 segundos — imprimindo apenas as médias globais de cada algoritmo.

---

## 9. Resultados Esperados

Um exemplo de saída do modo experimento (valores aproximados; os exatos variam por causa da aleatoriedade, mas convergem com as 100 repetições):

```
============================================================
           EXPERIMENTO DE ALOCACAO DE MEMORIA
============================================================
Execucoes por algoritmo: 100
Segundos por execucao: 100
============================================================

--------------------------------------------
Algoritmo: FIRST_FIT
Execucoes: 100
Tamanho medio dos processos: ~30
Ocupacao media da memoria: ~85-90%
Taxa media de descarte: ~5-10%
--------------------------------------------
Algoritmo: NEXT_FIT
Execucoes: 100
Tamanho medio dos processos: ~30
Ocupacao media da memoria: ~85-90%
Taxa media de descarte: ~5-10%
--------------------------------------------
Algoritmo: BEST_FIT
Execucoes: 100
Tamanho medio dos processos: ~30
Ocupacao media da memoria: ~85-90%
Taxa media de descarte: ~10-15%
--------------------------------------------
Algoritmo: WORST_FIT
Execucoes: 100
Tamanho medio dos processos: ~30
Ocupacao media da memoria: ~80-85%
Taxa media de descarte: ~15-25%
--------------------------------------------
```

**Interpretação dos resultados:**

- **O tamanho médio deve ser ~30 para todos os algoritmos.** Como a carga de trabalho é a mesma (processos uniformes entre 10 e 50, cuja média teórica é 30), essa métrica serve como **verificação de sanidade** do experimento: se algum algoritmo apresentasse tamanho médio muito diferente, indicaria viés na geração ou na contagem.
- **A taxa de descarte é a métrica discriminante.** O First/Next Fit tendem a apresentar as menores taxas; o **Worst Fit** consistentemente apresenta a maior, pois destrói os grandes buracos que seriam necessários para processos grandes — exatamente o comportamento previsto pela teoria.
- **A ocupação média alta (~85%+) é esperada**, pois a taxa de chegada (2 processos/s, tamanho médio 30 → ~60 unidades/s) é levemente maior que a taxa de saída (1,5 processos/s × 30 ≈ 45 unidades/s), empurrando a memória para perto da saturação — condição na qual as diferenças entre os algoritmos se tornam visíveis.

---

## Conclusão

O projeto demonstra, na prática, a tradução de conceitos teóricos de gerenciamento de memória para um sistema de software:

1. **A `LinkedList<BlocoMemoria>`** espelha o modelo clássico de memória como lista encadeada de blocos adjacentes, com inserção/remoção O(1) — a complexidade exigida pelas operações de divisão e coalescência.
2. **Os quatro algoritmos** são implementados como estratégias de busca sobre essa lista, diferindo apenas no critério de seleção do buraco — e o Next Fit se distingue por manter **estado persistente** (o índice de partida).
3. **O `SimuladorMemoria`** materializa o modelo de eventos discretos (2 chegadas/s, 1–2 partidas/s) em um laço temporal com coleta de métricas instantâneas.
4. **O `ExperimentoAlocacao`** aplica o método estatístico de repetição (100 execuções independentes + média) para converter resultados ruidosos em conclusões confiáveis sobre a performance relativa dos algoritmos.

A taxa de descarte — processos que a memória não conseguiu acomodar — é, no fim, a medida que conecta teoria e prática: ela quantifica o custo real da fragmentação externa, o fenômeno central que motiva o estudo comparativo dos algoritmos de alocação contígua.
