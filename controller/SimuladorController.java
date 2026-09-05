package controller;

import model.TipoAlgoritmo;
import service.CPU;
import service.Escalonador;
import service.GeradorDeProcessos;

// Coordena a montagem do simulador: cria as entidades, prepara a fila inicial e inicia a CPU
public class SimuladorController {

    public void iniciarSimulacao(TipoAlgoritmo algoritmo, int quantum,
            int tempoClockMs, int cargaInicial) {

        // O gerador cria processos; o escalonador decide qual deles será entregue à CPU
        GeradorDeProcessos gerador = new GeradorDeProcessos();
        Escalonador escalonador = new Escalonador(algoritmo, quantum, gerador);

        System.out.println("========================================");
        System.out.println("       PREPARANDO AMBIENTE");
        System.out.println("========================================");

        // Cria a quantidade de processos que já existirão antes da CPU começar a executar
        for (int i = 0; i < cargaInicial; i++) {
            escalonador.adicionarProcesso(gerador.gerarProcesso());
        }

        // A CPU roda em uma thread própria e executa seus ciclos de clock
        CPU cpu = new CPU(escalonador, tempoClockMs);
        Thread threadCpu = new Thread(cpu);
        threadCpu.start();

        try {
            // Faz o controlador aguardar até que a execução da CPU termine
            threadCpu.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("\n========================================");
        System.out.println("   SIMULACAO ENCERRADA PELO CONTROLADOR");
        System.out.println("========================================");
    }
}
