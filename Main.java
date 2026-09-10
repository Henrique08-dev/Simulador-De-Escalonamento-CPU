import controller.SimuladorController;
import model.TipoAlgoritmo;

public class Main {
    public static void main(String[] args) {
        SimuladorController controller = new SimuladorController();

        // Configuração da simulação: algoritmo, quantum, tempo de cada clock e carga inicial
        controller.iniciarSimulacao(
                TipoAlgoritmo.ROUND_ROBIN,
                2,
                1000,
                2);
    }
}
