package service;

import model.Processo;

// Implementa Runnable para que a CPU execute seus ciclos em uma thread própria
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

        System.out.println("\n========================================");
        System.out.println("     INICIANDO SIMULACAO CONTROLADA");
        System.out.println("========================================");

        // A CPU continua enquanto existir processo na fila ou algum processo atualmente em execução
        while (escalonador.possuiProcessos() || processoAtual != null) {
            System.out.printf("\n[CICLO %d]\n", cicloClock);

            // A CPU não escolhe o algoritmo: pergunta ao Escalonador qual processo deve executar
            processoAtual = escalonador.obterProximoProcesso(processoAtual);

            if (processoAtual != null) {

                // Um ciclo de clock executa exatamente 1 instrução do processo atual
                processoAtual.registrarExecucao((int) cicloClock);
                System.out.printf("  CPU executando PID %d | Restam: %d instrucoes\n",
                        processoAtual.getId(), processoAtual.getQuantidadeInstrucoes());

                if (processoAtual.isFinalizado()) {
                    System.out.printf("  [X] PID %d FINALIZADO!\n", processoAtual.getId());
                    processoAtual.imprimirHistorico();

                    // Libera a CPU para que o escalonador escolha outro processo no próximo ciclo
                    processoAtual = null;
                }
            } else {
                System.out.println("  CPU em estado ocioso, aguardando novos processos.");
            }

            cicloClock++;

            try {
                // Define a duração visual de cada ciclo para facilitar o acompanhamento da simulação
                Thread.sleep(msPorCiclo);
            } catch (InterruptedException e) {
                break;
            }
        }

        System.out.println("\n========================================");
        System.out.println("           CPU DESLIGADA");
        System.out.println("========================================");
    }
}
