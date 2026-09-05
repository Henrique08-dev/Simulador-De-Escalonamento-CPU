package service;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import model.Processo;

public class GeradorDeProcessos {
    // Mantém os IDs únicos e incrementais: 1, 2, 3, 4...
    private final AtomicInteger contadorId = new AtomicInteger(1);
    private final Random random = new Random();

    public Processo gerarProcesso() {
        int id = contadorId.getAndIncrement();

        // nextInt(41) gera 0 a 40; somando 10, o processo terá de 10 a 50 instruções
        int instrucoes = random.nextInt(41) + 10;
        return new Processo(id, instrucoes);
    }
}
