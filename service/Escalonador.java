package service;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;
import model.Processo;
import model.TipoAlgoritmo;

// Entidade passiva: não executa processos; apenas decide qual processo a CPU receberá
public class Escalonador {
    private final TipoAlgoritmo algoritmo;
    private final int quantum;
    private int quantumAtual;
    private final GeradorDeProcessos gerador;
    private final Random random = new Random();

    // FCFS e Round Robin usam filas comuns; SJF usa prioridade pela menor
    // quantidade de instruções
    private final Queue<Processo> filaFCFS = new LinkedList<>();
    private final PriorityQueue<Processo> filaSJF = new PriorityQueue<>(
            Comparator.comparingInt(Processo::getQuantidadeInstrucoes));
    private final Queue<Processo> filaRoundRobin = new LinkedList<>();

    public Escalonador(TipoAlgoritmo algoritmo, int quantum, GeradorDeProcessos gerador) {
        this.algoritmo = algoritmo;
        this.quantum = quantum;
        this.quantumAtual = 0;
        this.gerador = gerador;
    }

    // Adiciona o processo somente na fila correspondente ao algoritmo escolhido
    // para a simulação
    public synchronized void adicionarProcesso(Processo p) {
        System.out.printf("  [+] NOVO PROCESSO NA FILA: %s\n", p);
        switch (algoritmo) {
            case FCFS -> filaFCFS.add(p);
            case SJF -> filaSJF.add(p);
            case ROUND_ROBIN -> filaRoundRobin.add(p);
        }
    }

    // A CPU chama este método a cada ciclo para saber qual processo deve continuar
    // ou assumir a CPU
    public synchronized Processo obterProximoProcesso(Processo processoAtual) {
        // Simula a chegada de processos durante a execução: 25% de chance a cada
        // requisição da CPU.
        if (random.nextDouble() < 0.25) {
            Processo novo = gerador.gerarProcesso();
            adicionarProcesso(novo);
        }

        // Direciona a decisão para a regra do algoritmo configurado
        switch (algoritmo) {
            case FCFS:
                return escalonarFCFS(processoAtual);
            case SJF:
                return escalonarSJF(processoAtual);
            case ROUND_ROBIN:
                return escalonarRoundRobin(processoAtual);
            default:
                return null;
        }
    }

    // FCFS: o primeiro que entrou é o primeiro a executar e permanece na CPU até
    // finalizar
    private Processo escalonarFCFS(Processo atual) {
        if (atual != null && !atual.isFinalizado())
            return atual;
        return filaFCFS.poll();
    }

    // SJF: quando a CPU fica livre, a PriorityQueue entrega o processo com menos
    // instruções
    // Também é não preemptivo: um processo que já está executando continua até
    // finalizar
    private Processo escalonarSJF(Processo atual) {
        if (atual != null && !atual.isFinalizado())
            return atual;
        return filaSJF.poll();
    }

    // Round Robin: cada processo pode usar a CPU apenas pela quantidade de ciclos
    // definida no quantum
    private Processo escalonarRoundRobin(Processo atual) {
        if (atual != null) {
            if (atual.isFinalizado()) {
                // Processo terminou antes ou exatamente no limite do quantum
                quantumAtual = 0;
            } else if (quantumAtual >= quantum) {
                // Quantum acabou: ocorre preempção e o processo volta para o final da fila
                System.out.printf("  [!] PREEMPCAO: Quantum (%d) expirado para PID %d. Reenfileirando.\n",
                        quantum, atual.getId());
                filaRoundRobin.add(atual);
                quantumAtual = 0;
            } else {
                // Ainda possui quantum: o mesmo processo continua na CPU por mais um ciclo
                quantumAtual++;
                return atual;
            }
        }

        // Retira o próximo processo do início da fila e inicia a contagem do seu
        // quantum
        Processo proximo = filaRoundRobin.poll();
        if (proximo != null)
            quantumAtual = 1;
        return proximo;
    }

    // Informa à CPU se ainda existe algum processo aguardando em alguma fila
    public synchronized boolean possuiProcessos() {
        return !filaFCFS.isEmpty() || !filaSJF.isEmpty() || !filaRoundRobin.isEmpty();
    }
}
