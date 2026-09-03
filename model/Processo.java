package model;

import java.util.ArrayList;
import java.util.List;

public class Processo {
    private final int id;
    private int quantidadeInstrucoes;
    private final int instrucoesTotais;
    private final List<Integer> historicoExecucao;

    public Processo(int id, int quantidadeInstrucoes) {
        this.id = id;
        this.quantidadeInstrucoes = quantidadeInstrucoes;
        this.instrucoesTotais = quantidadeInstrucoes;
        this.historicoExecucao = new ArrayList<>();
    }

    public int getId() {
        return id;
    }

    public int getQuantidadeInstrucoes() {
        return quantidadeInstrucoes;
    }

    public int getInstrucoesTotais() {
        return instrucoesTotais;
    }

    public void registrarExecucao(int cicloClock) {
        if (this.quantidadeInstrucoes > 0) {
            this.historicoExecucao.add(cicloClock);
            this.quantidadeInstrucoes--;
        }
    }

    public boolean isFinalizado() {
        return this.quantidadeInstrucoes == 0;
    }

    public void imprimirHistorico() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < historicoExecucao.size(); i++) {
            sb.append(String.format("Instrução %d→Ciclo %d", i + 1, historicoExecucao.get(i)));
            if (i < historicoExecucao.size() - 1) sb.append(", ");
        }
        System.out.printf("  [HISTÓRICO PID %d] %s\n", id, sb.toString());
    }

    @Override
    public String toString() {
        return String.format("[PID: %d | Instruções: %d/%d]", 
            id, quantidadeInstrucoes, instrucoesTotais);
    }
}