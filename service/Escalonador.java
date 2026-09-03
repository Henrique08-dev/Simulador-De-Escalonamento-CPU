package service;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;
import model.Processo;
import model.TipoAlgoritmo;

public class Escalonador {
    private final TipoAlgoritmo algoritmo;
    private final int quantum;
    private int quantumAtual;
    private final GeradorDeProcessos gerador;
    private final Random random = new Random();

    private final Queue<Processo> filaFCFS = new LinkedList<>();
    private final PriorityQueue<Processo> filaSJF = new PriorityQueue<>(
            Comparator.comparingInt(Processo::getQuantidadeInstrucoes)
    );
    private final Queue<Processo> filaRoundRobin = new LinkedList<>();

    public Escalonador(TipoAlgoritmo algoritmo, int quantum, GeradorDeProcessos gerador) {
        this.algoritmo = algoritmo;
        this.quantum = quantum;
        this.quantumAtual = 0;
        this.gerador = gerador;
    }

    public synchronized void adicionarProcesso(Processo p) {
        System.out.printf("  [+] NOVO PROCESSO NA FILA: %s\n", p);
        switch (algoritmo) {
            case FCFS -> filaFCFS.add(p);
            case SJF -> filaSJF.add(p);
            case ROUND_ROBIN -> filaRoundRobin.add(p);
        }
    }

    public synchronized Processo obterProximoProcesso(Processo processoAtual) {
        if (random.nextDouble() < 0.25) {
            Processo novo = gerador.gerarProcesso();
            adicionarProcesso(novo);
        }

        switch (algoritmo) {
            case FCFS: return escalonarFCFS(processoAtual);
            case SJF: return escalonarSJF(processoAtual);
            case ROUND_ROBIN: return escalonarRoundRobin(processoAtual);
            default: return null;
        }
    }

    private Processo escalonarFCFS(Processo atual) {
        if (atual != null && !atual.isFinalizado()) return atual;
        return filaFCFS.poll();
    }

    private Processo escalonarSJF(Processo atual) {
        if (atual != null && !atual.isFinalizado()) return atual;
        return filaSJF.poll();
    }

    private Processo escalonarRoundRobin(Processo atual) {
        if (atual != null) {
            if (atual.isFinalizado()) {
                quantumAtual = 0;
            } else if (quantumAtual >= quantum) {
                System.out.printf("  [!] PREEMPÇÃO: Quantum (%d) expirado para PID %d. Reenfileirando.\n", 
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

    public synchronized boolean possuiProcessos() {
        return !filaFCFS.isEmpty() || !filaSJF.isEmpty() || !filaRoundRobin.isEmpty();
    }
}