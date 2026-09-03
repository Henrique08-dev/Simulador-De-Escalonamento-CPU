package controller;

import model.TipoAlgoritmo;
import service.CPU;
import service.Escalonador;
import service.GeradorDeProcessos;

public class SimuladorController {

    public void iniciarSimulacao(TipoAlgoritmo algoritmo, int quantum, 
                                 int tempoClockMs, int cargaInicial) {
        
        GeradorDeProcessos gerador = new GeradorDeProcessos();
        Escalonador escalonador = new Escalonador(algoritmo, quantum, gerador);

        System.out.println("=== Preparando Ambiente ===");
        for (int i = 0; i < cargaInicial; i++) {
            escalonador.adicionarProcesso(gerador.gerarProcesso());
        }

        CPU cpu = new CPU(escalonador, tempoClockMs);
        Thread threadCpu = new Thread(cpu);
        threadCpu.start();

        try {
            threadCpu.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("\n=== Simulação Encerrada ===");
    }
}