package service;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import model.Processo;

public class GeradorDeProcessos {
    private final AtomicInteger contadorId = new AtomicInteger(1);
    private final Random random = new Random();

    public Processo gerarProcesso() {
        int id = contadorId.getAndIncrement();
        int instrucoes = random.nextInt(41) + 10; 
        return new Processo(id, instrucoes);
    }
}