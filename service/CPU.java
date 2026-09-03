package service;

import model.Processo;

public class CPU implements Runnable {
    private final Escalonador escalonador;
    private final int msPorCiclo;

    public CPU(Escalonador escalonador, int msPorCiclo) {
        this.escalonador = escalonador;
        this.msPorCiclo = msPorCiclo;
    }

    @Override
    public void run() {
        Processo processoAtual = null;
        long cicloClock = 1;

        System.out.println("\n=== INICIANDO SIMULAÇÃO ===");

        while (escalonador.possuiProcessos() || processoAtual != null) {
            System.out.printf("\n[CICLO %d]\n", cicloClock);

            processoAtual = escalonador.obterProximoProcesso(processoAtual);

            if (processoAtual != null) {

                processoAtual.registrarExecucao((int) cicloClock);
                System.out.printf("  CPU executando PID %d | Restam: %d instruções\n", 
                        processoAtual.getId(), processoAtual.getQuantidadeInstrucoes());

                if (processoAtual.isFinalizado()) {
                    System.out.printf("  [X] PID %d FINALIZADO!\n", processoAtual.getId());
                    processoAtual.imprimirHistorico();
                    processoAtual = null;
                }
            } else {
                System.out.println("  CPU em estado ocioso — aguardando novos processos.");
            }

            cicloClock++;

            try {
                Thread.sleep(msPorCiclo);
            } catch (InterruptedException e) {
                break;
            }
        }

        System.out.println("\n=== CPU DESLIGADA: Todos os processos finalizados ===");
    }
}